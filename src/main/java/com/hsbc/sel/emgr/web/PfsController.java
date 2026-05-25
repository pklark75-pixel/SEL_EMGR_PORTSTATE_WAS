package com.hsbc.sel.emgr.web;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.BatchValidationResult;
import com.hsbc.sel.emgr.model.QueuePage;
import com.hsbc.sel.emgr.model.QueueRecord;
import com.hsbc.sel.emgr.model.QueueSummary;
import com.hsbc.sel.emgr.model.UploadHistoryRecord;
import com.hsbc.sel.emgr.service.PfsBatchService;
import com.hsbc.sel.emgr.service.PfsEmailQueueService;
import com.hsbc.sel.emgr.service.PfsStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PfsController {

    private static final Logger log = LoggerFactory.getLogger(PfsController.class);

    private static final int[] VALID_PAGE_SIZES = {10, 20, 50, 100};

    @Value("${app.env:DEV}")
    private String appEnv;

    private final PfsProperties properties;
    private final PfsStorageService storageService;
    private final PfsBatchService batchService;
    private final PfsEmailQueueService queueService;

    public PfsController(PfsProperties properties, PfsStorageService storageService,
                         PfsBatchService batchService, PfsEmailQueueService queueService) {
        this.properties = properties;
        this.storageService = storageService;
        this.batchService = batchService;
        this.queueService = queueService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/pfs";
    }

    @GetMapping("/pfs")
    public String home(@RequestParam(value = "filterTime", required = false) String filterTime,
                       @RequestParam(value = "appFilter",  required = false) String appFilter,
                       @RequestParam(value = "sendFlag",   required = false) String sendFlag,
                       @RequestParam(value = "keyword",    required = false) String keyword,
                       @RequestParam(value = "dateFrom",   required = false) String dateFrom,
                       @RequestParam(value = "dateTo",     required = false) String dateTo,
                       @RequestParam(value = "page",       defaultValue = "0") int page,
                       @RequestParam(value = "pageSize",   defaultValue = "10") int pageSize,
                       Model model) {

        int safePageSize = 10;
        for (int v : VALID_PAGE_SIZES) { if (pageSize == v) { safePageSize = v; break; } }

        model.addAttribute("appEnv",           appEnv);
        model.addAttribute("uploadHistoryRows", properties.getUploadHistoryRows());
        model.addAttribute("filterTime", filterTime);
        model.addAttribute("appFilter",  appFilter);
        model.addAttribute("sendFlag",   sendFlag);
        model.addAttribute("keyword",    keyword);
        model.addAttribute("dateFrom",   dateFrom);
        model.addAttribute("dateTo",     dateTo);
        model.addAttribute("page",       page);
        model.addAttribute("pageSize",   safePageSize);

        try {
            model.addAttribute("stats", queueService.getSummaryStats());
        } catch (Exception ex) {
            model.addAttribute("stats", new com.hsbc.sel.emgr.model.DashboardStats());
        }

        try {
            List<UploadHistoryRecord> histories = queueService.getRecentUploadHistories(properties.getUploadHistoryRows());
            model.addAttribute("uploadHistories", histories);
        } catch (Exception ex) {
            model.addAttribute("uploadHistories", Collections.emptyList());
            model.addAttribute("dbError", "DB 연결 실패 (이력 조회): " + ex.getMessage());
        }

        try {
            QueuePage queuePage = queueService.getQueuePage(filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo, page, safePageSize);
            model.addAttribute("queueRecords", queuePage.getRecords());
            model.addAttribute("totalCount",   queuePage.getTotalCount());
            model.addAttribute("totalPages",   queuePage.getTotalPages());
            model.addAttribute("page",         queuePage.getPage());
        } catch (Exception ex) {
            model.addAttribute("queueRecords", Collections.emptyList());
            model.addAttribute("totalCount", 0);
            model.addAttribute("totalPages", 1);
            model.addAttribute("page", 0);
            if (!model.containsAttribute("dbError")) {
                model.addAttribute("dbError", "DB 연결 실패 (큐 조회): " + ex.getMessage());
            }
        }

        return "pfs";
    }

    @PostMapping("/pfs/queue/delete")
    public String deleteQueue(@RequestParam(value = "smtpIds", required = false) List<String> smtpIds,
                              RedirectAttributes redirect) {
        if (smtpIds == null || smtpIds.isEmpty()) {
            redirect.addFlashAttribute("error", "삭제할 레코드를 선택하세요.");
            return "redirect:/pfs";
        }
        Map<Long, String> targets = new LinkedHashMap<>();
        for (String token : smtpIds) {
            String[] parts = token.split(":", 2);
            if (parts.length == 2) {
                try {
                    targets.put(Long.parseLong(parts[0].trim()), parts[1].trim());
                } catch (NumberFormatException ex) {
                    log.warn("invalid smtpId token: {}", token);
                }
            }
        }
        int deleted = 0;
        try {
            deleted = queueService.deleteQueueRecords(targets);
        } catch (Exception ex) {
            log.warn("batch queue delete failed: {}", ex.getMessage());
            redirect.addFlashAttribute("error", "삭제 실패: " + ex.getMessage());
            return "redirect:/pfs";
        }
        redirect.addFlashAttribute("message", deleted + "건 삭제 완료");
        return "redirect:/pfs";
    }

    @PostMapping("/pfs/upload-zip")
    public String uploadZip(@RequestParam(value = "batchZip", required = false) MultipartFile batchZip,
                            RedirectAttributes redirect,
                            Principal principal) {
        String uploadUser = principal != null ? principal.getName() : "-";

        if (batchZip == null || batchZip.isEmpty()) {
            saveUploadHistorySafe(false, 0, 0, 0, "ZIP 파일 미탐지", uploadUser);
            redirect.addFlashAttribute("error", "ZIP 파일을 찾지 못했습니다.");
            return "redirect:/pfs";
        }

        byte[] zipBytes;
        try {
            zipBytes = batchZip.getBytes();
        } catch (Exception ex) {
            saveUploadHistorySafe(false, 0, 0, 0, "파일 읽기 실패", uploadUser);
            redirect.addFlashAttribute("error", "파일 읽기 실패: " + ex.getMessage());
            return "redirect:/pfs";
        }

        if (zipBytes.length > properties.getUploadZipMaxBytes()) {
            saveUploadHistorySafe(false, 0, 0, 0, "파일 크기 초과", uploadUser);
            redirect.addFlashAttribute("error", "ZIP 파일 크기가 허용 한도를 초과합니다.");
            return "redirect:/pfs";
        }

        try {
            storageService.clearDirectory(properties.getBatchDirPath());
            int imported = storageService.importBatchFilesFromZip(zipBytes, true);

            BatchValidationResult validation = batchService.validateBatchFiles();
            if (!validation.isValid()) {
                saveUploadHistorySafe(false, imported, 0, 0, "검증 실패", uploadUser);
                redirect.addFlashAttribute("error",
                    "ZIP 업로드 후 검증 실패: " + String.join(" | ", validation.getErrors()));
                return "redirect:/pfs";
            }

            QueueSummary queue = queueService.queueEmailsFromGeneratedHtml();
            saveUploadHistorySafe(true, imported, queue.getHsbcQueuedCount(), queue.getHredQueuedCount(), "성공", uploadUser);

            redirect.addFlashAttribute("message",
                "ZIP 업로드/큐저장 완료. imported=" + imported
                + ", reportDate=" + validation.getReportDate()
                + ", queued(HSBC/HRED)=" + queue.getHsbcQueuedCount() + "/" + queue.getHredQueuedCount()
                + ", verify(files/recv/info)=" + queue.getFilesTableCount() + "/" + queue.getRecvTableCount() + "/" + queue.getInfoTableCount()
                + ", smtpIdRange=" + queue.getMinSmtpId() + "-" + queue.getMaxSmtpId());

        } catch (Exception ex) {
            saveUploadHistorySafe(false, 0, 0, 0, "실패: " + ex.getMessage(), uploadUser);
            redirect.addFlashAttribute("error", "ZIP 자동 처리 실패: " + ex.getMessage());
        } finally {
            storageService.clearDirectory(properties.getBatchDirPath());
        }

        return "redirect:/pfs";
    }

    @GetMapping("/pfs/html-file")
    @ResponseBody
    public ResponseEntity<byte[]> htmlFile(@RequestParam("id") long id,
                                            @RequestParam("app") String app,
                                            @RequestParam("name") String name) {
        if (name == null || !name.endsWith(".html") || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        byte[] bytes = queueService.getHtmlFileBytes(id, app, name);
        if (bytes == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .header("Content-Security-Policy", "sandbox allow-forms")
            .body(bytes);
    }

    private void saveUploadHistorySafe(boolean success, int imported, int hsbc, int hred, String message, String uploadUser) {
        try { queueService.saveUploadHistory(success, imported, hsbc, hred, message, uploadUser); }
        catch (Exception ex) { log.warn("upload history save failed: {}", ex.getMessage()); }
    }
}
