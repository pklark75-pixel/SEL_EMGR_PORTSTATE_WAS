package com.hsbc.sel.emgr.jdk;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.BatchValidationResult;
import com.hsbc.sel.emgr.model.QueueRecord;
import com.hsbc.sel.emgr.model.QueueSummary;
import com.hsbc.sel.emgr.model.UploadHistoryRecord;
import com.hsbc.sel.emgr.service.PfsBatchService;
import com.hsbc.sel.emgr.service.PfsEmailQueueService;
import com.hsbc.sel.emgr.service.PfsStorageService;
import com.hsbc.sel.emgr.service.PfsTemplateService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class PfsHttpServer {

    private final PfsProperties properties;
    private final PfsStorageService storageService;
    private final PfsBatchService batchService;
    private final PfsEmailQueueService queueService;

    private PfsHttpServer(PfsProperties properties, PfsStorageService storageService, PfsBatchService batchService, PfsEmailQueueService queueService) {
        this.properties = properties;
        this.storageService = storageService;
        this.batchService = batchService;
        this.queueService = queueService;
    }

    public static HttpServer start(int port) throws IOException {
        PfsProperties properties = PfsProperties.loadDefault();
        PfsStorageService storageService = new PfsStorageService(properties);
        PfsTemplateService templateService = new PfsTemplateService(properties);
        PfsBatchService batchService = new PfsBatchService(properties, storageService, templateService);
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(properties.getDbUrl());
        hikariConfig.setDriverClassName(properties.getDbDriverClass());
        hikariConfig.setUsername(properties.getDbUser());
        hikariConfig.setPassword(properties.getEffectiveDbPassword());
        hikariConfig.setMaximumPoolSize(2);
        hikariConfig.setPoolName("pfs-http");
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        PfsEmailQueueService queueService = new PfsEmailQueueService(properties, batchService, templateService, dataSource);

        PfsHttpServer app = new PfsHttpServer(properties, storageService, batchService, queueService);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", app::handle);
        server.setExecutor(null);
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        try {
            if ("/".equals(path)) {
                redirect(exchange, "/pfs");
                return;
            }

            if ("/pfs".equals(path)) {
                renderHome(exchange, null, null);
                return;
            }

            if ("/pfs/upload-zip".equals(path)) {
                handleUploadZip(exchange);
                return;
            }

            if ("/pfs/html-file".equals(path)) {
                handleHtmlFile(exchange);
                return;
            }

            sendText(exchange, 404, "Not found");
        } catch (Exception ex) {
            renderHome(exchange, null, "처리 중 오류: " + ex.getMessage());
        }
    }

    private void handleHtmlFile(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            sendText(exchange, 400, "Missing query parameters");
            return;
        }

        String smtpIdStr = null, appName = null, name = null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length < 2) continue;
            switch (kv[0]) {
                case "id":  smtpIdStr = kv[1]; break;
                case "app": appName   = java.net.URLDecoder.decode(kv[1], "UTF-8"); break;
                case "name": name     = java.net.URLDecoder.decode(kv[1], "UTF-8"); break;
            }
        }

        if (smtpIdStr == null || appName == null || name == null) {
            sendText(exchange, 400, "Required: id, app, name");
            return;
        }

        long smtpId;
        try {
            smtpId = Long.parseLong(smtpIdStr);
        } catch (NumberFormatException ex) {
            sendText(exchange, 400, "Invalid id");
            return;
        }

        byte[] htmlBytes = queueService.getHtmlFileBytes(smtpId, appName, name);
        if (htmlBytes == null) {
            sendText(exchange, 404, "File not found in DB: " + name);
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, htmlBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(htmlBytes);
        }
    }

    private void handleUploadZip(HttpExchange exchange) throws IOException {

        Headers headers = exchange.getRequestHeaders();
        String contentType = headers.getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data") || contentType.indexOf("boundary=") < 0) {
            saveUploadHistorySafe(false, 0, 0, 0, "잘못된 multipart/form-data 요청");
            renderHome(exchange, null, "multipart/form-data ZIP 업로드만 지원합니다.");
            return;
        }

        String boundary = contentType.substring(contentType.indexOf("boundary=") + 9).trim();
        byte[] bodyBytes = readAllBytes(exchange, properties.getUploadZipMaxBytes());
        UploadedFile file = parseMultipartZip(bodyBytes, boundary);
        if (file == null || file.payload.length == 0) {
            saveUploadHistorySafe(false, 0, 0, 0, "ZIP 파일 미탐지");
            renderHome(exchange, null, "ZIP 파일을 찾지 못했습니다.");
            return;
        }

        try {
            // Ensure upload works as a temporary pipeline run.
            storageService.clearDirectory(properties.getBatchDirPath());

            int imported = storageService.importBatchFilesFromZip(file.payload, true);

            BatchValidationResult validation = batchService.validateBatchFiles();
            if (!validation.isValid()) {
                saveUploadHistorySafe(false, imported, 0, 0, "검증 실패");
                renderHome(exchange, null, "ZIP 업로드 후 검증 실패: " + String.join(" | ", validation.getErrors()));
                return;
            }

            QueueSummary queue = queueService.queueEmailsFromGeneratedHtml();
            saveUploadHistorySafe(true, imported, queue.getHsbcQueuedCount(), queue.getHredQueuedCount(), "성공");

            String msg = "ZIP 업로드/큐저장 완료(파일 미보관). imported=" + imported
                + ", reportDate=" + validation.getReportDate()
                + ", queued(HSBC/HRED)=" + queue.getHsbcQueuedCount() + "/" + queue.getHredQueuedCount()
                + ", verify(files/recv/info)=" + queue.getFilesTableCount() + "/" + queue.getRecvTableCount() + "/" + queue.getInfoTableCount()
                + ", smtpIdRange=" + queue.getMinSmtpId() + "-" + queue.getMaxSmtpId();

            renderHome(exchange, msg, null);
        } catch (Exception ex) {
            saveUploadHistorySafe(false, 0, 0, 0, "실패: " + ex.getMessage());
            renderHome(exchange, null, "ZIP 자동 처리 실패: " + ex.getMessage());
        } finally {
            // Always clean up temporary files after upload processing.
            storageService.clearDirectory(properties.getBatchDirPath());
        }
    }

    private void renderHome(HttpExchange exchange, String message, String error) throws IOException {

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>PFS JDK Console</title>");
        html.append("<style>body{font-family:Arial;margin:24px;} .card{border:1px solid #ddd;border-radius:8px;padding:14px;margin-bottom:14px;} ");
        html.append(".ok{background:#eefaf0;border:1px solid #abd8b4;padding:10px;} .err{background:#fff2f2;border:1px solid #f1b7b7;padding:10px;} ul{margin:6px 0 0 20px;}</style>");
        html.append("</head><body>");
        html.append("<h2>PFS JDK Server</h2>");

        if (message != null) {
            html.append("<div class='ok'>").append(escape(message)).append("</div>");
        }
        if (error != null) {
            html.append("<div class='err'>").append(escape(error)).append("</div>");
        }

        html.append("<div class='card'>");
        html.append("<h3>Batch ZIP Upload</h3>");
        html.append("<form method='post' action='/pfs/upload-zip' enctype='multipart/form-data'>");
        html.append("<input type='file' name='batchZip' accept='.zip' required/> ");
        html.append("<button type='submit'>Upload ZIP</button></form>");
        html.append("<p>필수 파일 6건: KRHRED/KRHSBC * CustAttr/CustSubs/EmailCustMast</p>");
        html.append("</div>");

        renderUploadHistorySection(html);
        renderQueueRecordsSection(html);

        html.append("<div class='card'><h3>Batch Files</h3>");
        html.append("<ul>");
        for (String name : storageService.listFileNames(properties.getBatchDirPath())) {
            html.append("<li>").append(escape(name)).append("</li>");
        }
        html.append("</ul></div>");

        html.append("</body></html>");

        byte[] response = html.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void renderQueueRecordsSection(StringBuilder html) {
        html.append("<div class='card'><h3>Queue Recent Results (DB)</h3>");
        try {
            List<QueueRecord> rows = queueService.getQueuePage(null, null, null, null, null, null, 0, properties.getQueueRecentRows()).getRecords();
            html.append("<p>rows=").append(rows.size()).append("</p>");
            html.append("<table width='100%' border='1' cellspacing='0' cellpadding='4' style='border-collapse:collapse;font-size:12px'>");
            html.append("<tr><th>SMTP_ID</th><th>APP</th><th>REF_NO</th><th>RECEIVER</th><th>S_FLAG</th><th>C_TIME</th><th>HTML</th></tr>");
            for (QueueRecord row : rows) {
                String previewLink = "";
                String refNo = row.getRefNo();
                if (refNo != null && !refNo.trim().isEmpty()) {
                    String fileName = refNo.trim() + ".html";
                    String href = "/pfs/html-file?id=" + row.getSmtpId()
                        + "&app=" + java.net.URLEncoder.encode(row.getAppName() == null ? "" : row.getAppName(), "UTF-8")
                        + "&name=" + java.net.URLEncoder.encode(fileName, "UTF-8");
                    previewLink = "<a href='" + href + "' target='_blank'>미리보기</a>";
                }
                html.append("<tr>")
                    .append("<td>").append(row.getSmtpId()).append("</td>")
                    .append("<td>").append(escape(row.getAppName())).append("</td>")
                    .append("<td>").append(escape(row.getRefNo())).append("</td>")
                    .append("<td>").append(escape(row.getReceiver())).append("</td>")
                    .append("<td>").append(escape(row.getSendFlag())).append("</td>")
                    .append("<td>").append(escape(row.getCreatedTime())).append("</td>")
                    .append("<td>").append(previewLink).append("</td>")
                    .append("</tr>");
            }
            html.append("</table>");
        } catch (Exception ex) {
            html.append("<div class='err'>Queue 결과 조회 실패: ").append(escape(ex.getMessage())).append("</div>");
        }
        html.append("</div>");
    }

    private void renderUploadHistorySection(StringBuilder html) {
        html.append("<div class='card'><h3>지난 12건 업로드 처리 이력</h3>");
        List<UploadHistoryRecord> snapshot;
        try {
            snapshot = queueService.getRecentUploadHistories(properties.getUploadHistoryRows());
        } catch (Exception ex) {
            html.append("<div class='err'>업로드 이력 조회 실패: ").append(escape(ex.getMessage())).append("</div></div>");
            return;
        }
        html.append("<table width='100%' border='1' cellspacing='0' cellpadding='4' style='border-collapse:collapse;font-size:12px'>");
        html.append("<tr><th>시간</th><th>업로드 파일수</th><th>큐저장 건수(HSBC/HRED/TOTAL)</th><th>성공여부</th><th>메시지</th></tr>");
        for (UploadHistoryRecord row : snapshot) {
            int total = row.getHsbcQueued() + row.getHredQueued();
            html.append("<tr>")
                .append("<td>").append(escape(row.getEventTime())).append("</td>")
                .append("<td>").append(row.getImportedCount()).append("</td>")
                .append("<td>").append(row.getHsbcQueued()).append("/").append(row.getHredQueued()).append("/").append(total).append("</td>")
                .append("<td>").append(row.isSuccess() ? "Y" : "N").append("</td>")
                .append("<td>").append(escape(row.getMessage())).append("</td>")
                .append("</tr>");
        }
        if (snapshot.isEmpty()) {
            html.append("<tr><td colspan='5' align='center'>이력이 없습니다.</td></tr>");
        }
        html.append("</table></div>");
    }

    private void saveUploadHistorySafe(boolean success, int importedCount, int hsbcQueued, int hredQueued, String message) {
        String sysUser = System.getProperty("user.name", "-");
        try {
            queueService.saveUploadHistory(success, importedCount, hsbcQueued, hredQueued, message, sysUser);
        } catch (Exception ex) {
            // do not fail upload flow when audit logging fails
            System.out.println("[WARN] upload history save failed: " + ex.getMessage());
        }
    }


    private void sendText(HttpExchange exchange, int code, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(code, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }


    private byte[] readAllBytes(HttpExchange exchange, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = exchange.getRequestBody().read(buffer)) > -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalStateException("Uploaded ZIP is too large. maxBytes=" + maxBytes);
            }
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private UploadedFile parseMultipartZip(byte[] body, String boundary) {
        String bodyText = new String(body, StandardCharsets.ISO_8859_1);
        int fileNameIndex = bodyText.indexOf("filename=");
        if (fileNameIndex < 0) {
            return null;
        }

        int quoteStart = bodyText.indexOf('"', fileNameIndex);
        int quoteEnd = bodyText.indexOf('"', quoteStart + 1);
        String fileName = quoteStart > -1 && quoteEnd > -1 ? bodyText.substring(quoteStart + 1, quoteEnd) : "upload.zip";

        int dataStart = bodyText.indexOf("\r\n\r\n", quoteEnd);
        if (dataStart < 0) {
            return null;
        }
        dataStart += 4;

        String boundaryMarker = "\r\n--" + boundary;
        int dataEnd = bodyText.indexOf(boundaryMarker, dataStart);
        if (dataEnd < 0) {
            dataEnd = bodyText.length();
        }

        byte[] payload = bodyText.substring(dataStart, dataEnd).getBytes(StandardCharsets.ISO_8859_1);
        return new UploadedFile(fileName, payload);
    }

    private static final class UploadedFile {
        private final String fileName;
        private final byte[] payload;

        private UploadedFile(String fileName, byte[] payload) {
            this.fileName = fileName;
            this.payload = payload;
        }
    }


    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}


