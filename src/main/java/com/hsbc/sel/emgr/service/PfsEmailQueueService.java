package com.hsbc.sel.emgr.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.BatchValidationResult;
import com.hsbc.sel.emgr.model.QueueRecord;
import com.hsbc.sel.emgr.model.QueueSummary;
import com.hsbc.sel.emgr.model.UploadHistoryRecord;

public class PfsEmailQueueService {

    private final PfsProperties properties;
    private final PfsBatchService batchService;
    private final PfsTemplateService templateService;
    private final AtomicInteger idSequence = new AtomicInteger(0);
    private final AtomicInteger auditSequence = new AtomicInteger(0);

    public PfsEmailQueueService(PfsProperties properties, PfsBatchService batchService, PfsTemplateService templateService) {
        this.properties = properties;
        this.batchService = batchService;
        this.templateService = templateService;
    }

    public QueueSummary queueEmailsFromGeneratedHtml() {
        if (!properties.isDbQueueEnabled()) {
            throw new IllegalStateException("DB queue is disabled. Set db.queue.enabled=true in files/pfs-jdk.properties");
        }

        BatchValidationResult validation = batchService.validateBatchFiles();
        if (!validation.isValid()) {
            throw new IllegalStateException("Batch validation failed: " + String.join(" | ", validation.getErrors()));
        }

        String content = templateService.readEmailContentTemplate();

        try {
            Class.forName(properties.getDbDriverClass());
        } catch (Exception ex) {
            throw new IllegalStateException("DB driver loading failed: " + properties.getDbDriverClass(), ex);
        }

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(properties.getDbUrl(), properties.getDbUser(), properties.getEffectiveDbPassword());
            conn.setAutoCommit(false);

            QueueSummary summary = new QueueSummary();

            List<com.hsbc.sel.emgr.model.Customer> hsbcCustomers = batchService.loadCustomers(validation, false);
            List<com.hsbc.sel.emgr.model.Customer> hredCustomers = batchService.loadCustomers(validation, true);

            QueueInsertResult hsbc = queueForCustomers(conn, hsbcCustomers, false, content);
            QueueInsertResult hred = queueForCustomers(conn, hredCustomers, true,  content);

            summary.setHsbcQueuedCount(hsbc.count);
            summary.setHredQueuedCount(hred.count);

            long minId = minPositive(hsbc.minSmtpId, hred.minSmtpId);
            long maxId = Math.max(hsbc.maxSmtpId, hred.maxSmtpId);
            summary.setMinSmtpId(minId);
            summary.setMaxSmtpId(maxId);

            if (minId > 0 && maxId > 0) {
                summary.setFilesTableCount(countRowsByRange(conn, properties.getQueueFilesTable(), minId, maxId));
                summary.setRecvTableCount(countRowsByRange(conn, properties.getQueueRecvTable(), minId, maxId));
                summary.setInfoTableCount(countRowsByRange(conn, properties.getQueueInfoTable(), minId, maxId));
            }

            conn.commit();
            return summary;

        } catch (Exception ex) {
            if (conn != null) {
                try { conn.rollback(); } catch (Exception re) { /* ignore */ }
            }
            throw new IllegalStateException("Queue insert failed", ex);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ce) { /* ignore */ }
            }
        }
    }

    // ── 큐 레코드 조회 (페이징 + 필터 통합) ─────────────────────────────────

    public List<QueueRecord> getQueueRecords(String filterTime, String appFilter, int page, int pageSize) {
        loadDriver();

        String where = buildWhere(filterTime, appFilter);
        int offset = Math.max(page, 0) * pageSize;

        String sql = "SELECT I.SMTP_ID, I.APP_NAME, I.SOURCE_APP, I.REF_NO, R.RECEIVER, I.S_FLAG, I.C_TIME, I.FUND_COUNT,"
            + " COALESCE(OCTET_LENGTH(F.DATAFILE), 0) AS FILE_SIZE"
            + " FROM " + properties.getQueueInfoTable() + " I"
            + " LEFT JOIN " + properties.getQueueRecvTable() + " R"
            + " ON I.SMTP_ID = R.SMTP_ID AND I.APP_NAME = R.APP_NAME"
            + " LEFT JOIN " + properties.getQueueFilesTable() + " F"
            + " ON I.SMTP_ID = F.SMTP_ID AND I.APP_NAME = F.APP_NAME"
            + " " + where
            + " ORDER BY I.C_TIME DESC, I.SMTP_ID DESC"
            + " OFFSET " + offset + " ROWS FETCH FIRST " + pageSize + " ROWS ONLY";

        List<QueueRecord> rows = new ArrayList<QueueRecord>();
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, filterTime, appFilter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QueueRecord r = new QueueRecord();
                    r.setSmtpId(rs.getLong(1));
                    r.setAppName(rs.getString(2));
                    r.setSourceApp(rs.getString(3));
                    r.setRefNo(rs.getString(4));
                    r.setReceiver(rs.getString(5));
                    r.setSendFlag(rs.getString(6));
                    Timestamp ts = rs.getTimestamp(7);
                    r.setCreatedTime(ts == null ? "" : ts.toString());
                    r.setFundCount(rs.getInt(8));
                    r.setFileSize(rs.getLong(9));
                    rows.add(r);
                }
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read queue records", ex);
        }
    }

    public int countQueueRecords(String filterTime, String appFilter) {
        loadDriver();

        String where = buildWhere(filterTime, appFilter);
        String sql = "SELECT COUNT(*) FROM " + properties.getQueueInfoTable() + " I " + where;

        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, filterTime, appFilter);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to count queue records", ex);
        }
    }

    // ── WHERE 절 빌더 ────────────────────────────────────────────────────────

    private String buildWhere(String filterTime, String appFilter) {
        List<String> conditions = new ArrayList<String>();
        if (hasValue(filterTime)) {
            conditions.add("DATE_TRUNC('minute', I.C_TIME) = DATE_TRUNC('minute', CAST(? AS TIMESTAMP))");
        }
        if (hasValue(appFilter)) {
            conditions.add("I.SOURCE_APP = ?");
        }
        return conditions.isEmpty() ? "" : "WHERE " + join(" AND ", conditions);
    }

    private void bindParams(PreparedStatement ps, String filterTime, String appFilter) throws Exception {
        int idx = 1;
        if (hasValue(filterTime)) ps.setString(idx++, filterTime);
        if (hasValue(appFilter))  ps.setString(idx,   appFilter);
    }

    private boolean hasValue(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String join(String sep, List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    // ── HTML 파일 조회 ───────────────────────────────────────────────────────

    public byte[] getHtmlFileBytes(long smtpId, String appName, String name) {
        String sql = "SELECT DATAFILE FROM " + properties.getQueueFilesTable()
            + " WHERE SMTP_ID = ? AND APP_NAME = ? AND NAME = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, smtpId);
            ps.setString(2, appName);
            ps.setString(3, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes(1);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read html file from DB", ex);
        }
        return null;
    }

    // ── 업로드 이력 ──────────────────────────────────────────────────────────

    public void saveUploadHistory(boolean success, int importedCount, int hsbcQueued, int hredQueued, String message) {
        loadDriver();

        String sql = "INSERT INTO " + properties.getUploadAuditTable()
            + " (AUDIT_ID, EVENT_TIME, IMPORTED_COUNT, HSBC_QUEUED, HRED_QUEUED, SUCCESS_FLAG, MESSAGE)"
            + " VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?)";

        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nextAuditId());
            ps.setInt(2, importedCount);
            ps.setInt(3, hsbcQueued);
            ps.setInt(4, hredQueued);
            ps.setString(5, success ? "Y" : "N");
            ps.setString(6, fit(message, 1000));
            ps.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save upload history", ex);
        }
    }

    public List<UploadHistoryRecord> getRecentUploadHistories(int limit) {
        loadDriver();
        int safeLimit = limit <= 0 ? 12 : Math.min(limit, 200);

        String sql = "SELECT EVENT_TIME, IMPORTED_COUNT, HSBC_QUEUED, HRED_QUEUED, SUCCESS_FLAG, MESSAGE"
            + " FROM " + properties.getUploadAuditTable()
            + " ORDER BY EVENT_TIME DESC, AUDIT_ID DESC"
            + " FETCH FIRST " + safeLimit + " ROWS ONLY";

        List<UploadHistoryRecord> rows = new ArrayList<UploadHistoryRecord>();
        try (Connection conn = openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UploadHistoryRecord r = new UploadHistoryRecord();
                Timestamp ts = rs.getTimestamp(1);
                r.setEventTime(ts == null ? "" : ts.toString());
                r.setImportedCount(rs.getInt(2));
                r.setHsbcQueued(rs.getInt(3));
                r.setHredQueued(rs.getInt(4));
                r.setSuccess("Y".equalsIgnoreCase(rs.getString(5)));
                r.setMessage(rs.getString(6));
                rows.add(r);
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read upload history", ex);
        }
    }

    // ── 큐 INSERT ────────────────────────────────────────────────────────────

    private QueueInsertResult queueForCustomers(Connection conn, List<com.hsbc.sel.emgr.model.Customer> customers,
                                                boolean isDbk, String content) throws Exception {
        QueueInsertResult result = new QueueInsertResult();
        if (customers == null || customers.isEmpty()) return result;

        String appName = properties.getAppName();
        String sourceApp = isDbk ? "KRHRED" : "KRHSBC";
        for (com.hsbc.sel.emgr.model.Customer customer : customers) {
            String refNo = customer.getCustNo();
            String email = customer.getEmailAddr();
            if (refNo == null || refNo.trim().isEmpty() || email == null || email.trim().isEmpty()) continue;

            String html = templateService.renderHtml(customer, isDbk);
            byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
            BigDecimal smtpId = nextSmtpId();

            insertFiles(conn, smtpId, refNo + ".html", htmlBytes, appName);
            insertRecv(conn, smtpId, email, appName);
            insertInfo(conn, smtpId, refNo, content, appName, sourceApp, customer.getStatements().size());
            result.count++;

            long id = smtpId.longValue();
            if (result.minSmtpId == 0 || id < result.minSmtpId) result.minSmtpId = id;
            if (id > result.maxSmtpId) result.maxSmtpId = id;
        }
        return result;
    }

    private void insertFiles(Connection conn, BigDecimal smtpId, String fileName, byte[] payload, String appName) throws Exception {
        String sql = "INSERT INTO " + properties.getQueueFilesTable()
            + " (SMTP_ID, APP_NAME, NAME, DATAFILE) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, smtpId);
            ps.setString(2, fit(appName, 20));
            ps.setString(3, fit(fileName, 255));
            ps.setBytes(4, payload);
            ps.executeUpdate();
        }
    }

    private void insertRecv(Connection conn, BigDecimal smtpId, String email, String appName) throws Exception {
        String sql = "INSERT INTO " + properties.getQueueRecvTable()
            + " (SMTP_ID, APP_NAME, RECEIVER) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, smtpId);
            ps.setString(2, fit(appName, 20));
            ps.setString(3, fit(email, 255));
            ps.executeUpdate();
        }
    }

    private void insertInfo(Connection conn, BigDecimal smtpId, String refNo, String content, String appName, String sourceApp, int fundCount) throws Exception {
        String sql = "INSERT INTO " + properties.getQueueInfoTable()
            + " (SMTP_ID, APP_NAME, REF_NO, SENDER, TITLE, CONTENT, C_TIME, S_CNT, S_FLAG, SOURCE_APP, FUND_COUNT)"
            + " VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 0, 'N', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, smtpId);
            ps.setString(2, fit(appName, 20));
            ps.setString(3, fit(refNo, 50));
            ps.setString(4, fit(properties.getSender(), 255));
            ps.setString(5, fit(properties.getTitle(), 255));
            ps.setString(6, fitUtf8Bytes(content, 3000));
            ps.setString(7, fit(sourceApp, 10));
            ps.setInt(8, fundCount);
            ps.executeUpdate();
        }
    }

    // ── 공통 유틸 ────────────────────────────────────────────────────────────

    private void loadDriver() {
        try { Class.forName(properties.getDbDriverClass()); }
        catch (Exception ex) { throw new IllegalStateException("DB driver loading failed: " + properties.getDbDriverClass(), ex); }
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(properties.getDbUrl(), properties.getDbUser(), properties.getEffectiveDbPassword());
    }

    private String fit(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private String fitUtf8Bytes(String value, int maxBytes) {
        if (value == null) return null;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return value;
        int end = value.length(), start = 0;
        while (start < end) {
            int mid = (start + end + 1) / 2;
            if (value.substring(0, mid).getBytes(StandardCharsets.UTF_8).length <= maxBytes) start = mid;
            else end = mid - 1;
        }
        return value.substring(0, start);
    }

    private BigDecimal nextSmtpId() {
        long base = System.currentTimeMillis() * 1000L;
        int seq = idSequence.updateAndGet(current -> (current + 1) % 1000);
        return new BigDecimal(base + seq);
    }

    private long nextAuditId() {
        long base = System.currentTimeMillis() * 1000L;
        int seq = auditSequence.updateAndGet(current -> (current + 1) % 1000);
        return base + seq;
    }

    private int countRowsByRange(Connection conn, String tableName, long minId, long maxId) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE SMTP_ID BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, minId);
            ps.setLong(2, maxId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private long minPositive(long a, long b) {
        if (a <= 0) return b;
        if (b <= 0) return a;
        return Math.min(a, b);
    }

    private static final class QueueInsertResult {
        private int count;
        private long minSmtpId;
        private long maxSmtpId;
    }
}
