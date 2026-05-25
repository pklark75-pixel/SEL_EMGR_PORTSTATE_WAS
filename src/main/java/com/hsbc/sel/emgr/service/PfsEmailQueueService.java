package com.hsbc.sel.emgr.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import com.hsbc.sel.emgr.config.PfsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hsbc.sel.emgr.model.BatchValidationResult;
import com.hsbc.sel.emgr.model.DashboardStats;
import com.hsbc.sel.emgr.model.QueuePage;
import com.hsbc.sel.emgr.model.QueueRecord;
import com.hsbc.sel.emgr.model.QueueSummary;
import com.hsbc.sel.emgr.model.UploadHistoryRecord;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

public class PfsEmailQueueService {

    private static final Logger log = LoggerFactory.getLogger(PfsEmailQueueService.class);

    private final PfsProperties properties;
    private final PfsBatchService batchService;
    private final PfsTemplateService templateService;
    private final DataSource dataSource;
    private final AtomicInteger idSequence = new AtomicInteger(0);
    private final AtomicInteger auditSequence = new AtomicInteger(0);

    public PfsEmailQueueService(PfsProperties properties, PfsBatchService batchService,
                                PfsTemplateService templateService, DataSource dataSource) {
        this.properties = properties;
        this.batchService = batchService;
        this.templateService = templateService;
        this.dataSource = dataSource;
    }

    @Transactional(rollbackFor = Exception.class)
    public QueueSummary queueEmailsFromGeneratedHtml() {
        if (!properties.isDbQueueEnabled()) {
            throw new IllegalStateException("DB queue is disabled. Set db.queue.enabled=true in files/pfs-jdk.properties");
        }

        BatchValidationResult validation = batchService.validateBatchFiles();
        if (!validation.isValid()) {
            throw new IllegalStateException("Batch validation failed: " + String.join(" | ", validation.getErrors()));
        }

        String content = templateService.readEmailContentTemplate();

        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
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

            return summary;

        } catch (Exception ex) {
            throw new IllegalStateException("Queue insert failed", ex);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    // ── 큐 레코드 조회 (페이징 + 필터 통합) ─────────────────────────────────

    @Transactional(readOnly = true)
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<QueueRecord> getQueueRecords(String filterTime, String appFilter, String sendFlag, String keyword, String dateFrom, String dateTo, int page, int pageSize) {
        String where = buildWhere(filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo);
        int offset = Math.max(page, 0) * pageSize;

        String sql = "SELECT I.SMTP_ID, I.APP_NAME, I.SOURCE_APP, I.REF_NO, R.RECEIVER, R.RECEIVER_NAME, I.S_FLAG, I.C_TIME, I.FUND_COUNT,"
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
            bindParams(ps, filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QueueRecord r = new QueueRecord();
                    r.setSmtpId(rs.getLong(1));
                    r.setAppName(rs.getString(2));
                    r.setSourceApp(rs.getString(3));
                    r.setRefNo(rs.getString(4));
                    r.setReceiver(rs.getString(5));
                    r.setReceiverName(rs.getString(6));
                    r.setSendFlag(rs.getString(7));
                    Timestamp ts = rs.getTimestamp(8);
                    r.setCreatedTime(ts == null ? "" : ts.toString());
                    r.setFundCount(rs.getInt(9));
                    r.setFileSize(rs.getLong(10));
                    rows.add(r);
                }
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read queue records", ex);
        }
    }

    @Transactional(readOnly = true)
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public int countQueueRecords(String filterTime, String appFilter, String sendFlag, String keyword, String dateFrom, String dateTo) {
        String where = buildWhere(filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo);
        String sql = "SELECT COUNT(*) FROM " + properties.getQueueInfoTable() + " I"
            + " LEFT JOIN " + properties.getQueueRecvTable() + " R"
            + " ON I.SMTP_ID = R.SMTP_ID AND I.APP_NAME = R.APP_NAME "
            + where;

        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to count queue records", ex);
        }
    }

    // ── 큐 페이지 조회 (count + records 동일 트랜잭션) ───────────────────────

    @Transactional(readOnly = true)
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public QueuePage getQueuePage(String filterTime, String appFilter, String sendFlag,
                                  String keyword, String dateFrom, String dateTo,
                                  int page, int pageSize) {
        String where = buildWhere(filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo);

        String countSql = "SELECT COUNT(*) FROM " + properties.getQueueInfoTable() + " I"
            + " LEFT JOIN " + properties.getQueueRecvTable() + " R"
            + " ON I.SMTP_ID = R.SMTP_ID AND I.APP_NAME = R.APP_NAME "
            + where;

        String recordSql = "SELECT I.SMTP_ID, I.APP_NAME, I.SOURCE_APP, I.REF_NO, R.RECEIVER, R.RECEIVER_NAME, I.S_FLAG, I.C_TIME, I.FUND_COUNT,"
            + " COALESCE(OCTET_LENGTH(F.DATAFILE), 0) AS FILE_SIZE"
            + " FROM " + properties.getQueueInfoTable() + " I"
            + " LEFT JOIN " + properties.getQueueRecvTable() + " R"
            + " ON I.SMTP_ID = R.SMTP_ID AND I.APP_NAME = R.APP_NAME"
            + " LEFT JOIN " + properties.getQueueFilesTable() + " F"
            + " ON I.SMTP_ID = F.SMTP_ID AND I.APP_NAME = F.APP_NAME"
            + " " + where
            + " ORDER BY I.C_TIME DESC, I.SMTP_ID DESC"
            + " OFFSET ? ROWS FETCH FIRST ? ROWS ONLY";

        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            int totalCount;
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                bindParams(ps, filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo);
                try (ResultSet rs = ps.executeQuery()) {
                    totalCount = rs.next() ? rs.getInt(1) : 0;
                }
            }

            int totalPages = totalCount == 0 ? 1 : (totalCount + pageSize - 1) / pageSize;
            int safePage   = Math.min(Math.max(page, 0), totalPages - 1);
            int offset     = safePage * pageSize;

            List<QueueRecord> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(recordSql)) {
                bindParams(ps, filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo);
                int idx = countBindParams(filterTime, appFilter, sendFlag, keyword, dateFrom, dateTo) + 1;
                ps.setInt(idx,     offset);
                ps.setInt(idx + 1, pageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        QueueRecord r = new QueueRecord();
                        r.setSmtpId(rs.getLong(1));
                        r.setAppName(rs.getString(2));
                        r.setSourceApp(rs.getString(3));
                        r.setRefNo(rs.getString(4));
                        r.setReceiver(rs.getString(5));
                        r.setReceiverName(rs.getString(6));
                        r.setSendFlag(rs.getString(7));
                        Timestamp ts = rs.getTimestamp(8);
                        r.setCreatedTime(ts == null ? "" : ts.toString());
                        r.setFundCount(rs.getInt(9));
                        r.setFileSize(rs.getLong(10));
                        rows.add(r);
                    }
                }
            }

            return new QueuePage(rows, totalCount, totalPages, safePage);

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read queue page", ex);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    // ── 대시보드 통계 ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public DashboardStats getSummaryStats() {
        DashboardStats stats = new DashboardStats();
        String qSql = "SELECT COUNT(*) FILTER (WHERE S_FLAG = 'N') AS p,"
            + " COUNT(*) FILTER (WHERE S_FLAG = 'Y') AS s, COUNT(*) AS t"
            + " FROM " + properties.getQueueInfoTable();
        String aSql = "SELECT COUNT(*) FROM " + properties.getUploadAuditTable()
            + " WHERE DATE(EVENT_TIME) = CURRENT_DATE";
        try (Connection conn = openConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(qSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.setPendingCount(rs.getInt("p"));
                    stats.setSentCount(rs.getInt("s"));
                    stats.setTotalQueueCount(rs.getInt("t"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(aSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.setTodayUploadCount(rs.getInt(1));
            }
        } catch (Exception ex) { log.warn("getSummaryStats DB error: {}", ex.getMessage()); }
        return stats;
    }

    // ── 큐 레코드 삭제 ───────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void deleteQueueRecord(long smtpId, String appName) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            for (String tbl : new String[]{properties.getQueueFilesTable(), properties.getQueueRecvTable(), properties.getQueueInfoTable()}) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tbl + " WHERE SMTP_ID = ? AND APP_NAME = ?")) {
                    ps.setLong(1, smtpId);
                    ps.setString(2, appName);
                    ps.executeUpdate();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Queue delete failed: smtpId=" + smtpId, ex);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    // ── WHERE 절 빌더 ────────────────────────────────────────────────────────

    private String buildWhere(String filterTime, String appFilter, String sendFlag, String keyword, String dateFrom, String dateTo) {
        List<String> conditions = new ArrayList<String>();
        if (hasValue(filterTime)) {
            conditions.add("DATE_TRUNC('minute', I.C_TIME) = DATE_TRUNC('minute', CAST(? AS TIMESTAMP))");
        }
        if (hasValue(appFilter)) {
            conditions.add("I.SOURCE_APP = ?");
        }
        if (hasValue(sendFlag)) {
            conditions.add("I.S_FLAG = ?");
        }
        if (hasValue(keyword)) {
            conditions.add("(UPPER(I.REF_NO) LIKE UPPER(?) OR UPPER(R.RECEIVER) LIKE UPPER(?))");
        }
        if (hasValue(dateFrom)) {
            conditions.add("I.C_TIME >= CAST(? AS DATE)");
        }
        if (hasValue(dateTo)) {
            conditions.add("I.C_TIME < CAST(? AS DATE) + INTERVAL '1 day'");
        }
        return conditions.isEmpty() ? "" : "WHERE " + join(" AND ", conditions);
    }

    private void bindParams(PreparedStatement ps, String filterTime, String appFilter, String sendFlag, String keyword, String dateFrom, String dateTo) throws Exception {
        int idx = 1;
        if (hasValue(filterTime)) ps.setString(idx++, filterTime);
        if (hasValue(appFilter))  ps.setString(idx++, appFilter);
        if (hasValue(sendFlag))   ps.setString(idx++, sendFlag);
        if (hasValue(keyword)) {
            String like = "%" + keyword.trim() + "%";
            ps.setString(idx++, like);
            ps.setString(idx++, like);
        }
        if (hasValue(dateFrom))   ps.setString(idx++, dateFrom);
        if (hasValue(dateTo))     ps.setString(idx++, dateTo);
    }

    private int countBindParams(String filterTime, String appFilter, String sendFlag, String keyword, String dateFrom, String dateTo) {
        int count = 0;
        if (hasValue(filterTime)) count++;
        if (hasValue(appFilter))  count++;
        if (hasValue(sendFlag))   count++;
        if (hasValue(keyword))    count += 2;
        if (hasValue(dateFrom))   count++;
        if (hasValue(dateTo))     count++;
        return count;
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

    public void saveUploadHistory(boolean success, int importedCount, int hsbcQueued, int hredQueued, String message, String uploadUser) {
        String sql = "INSERT INTO " + properties.getUploadAuditTable()
            + " (AUDIT_ID, EVENT_TIME, IMPORTED_COUNT, HSBC_QUEUED, HRED_QUEUED, SUCCESS_FLAG, MESSAGE, UPLOAD_USER)"
            + " VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nextAuditId());
            ps.setInt(2, importedCount);
            ps.setInt(3, hsbcQueued);
            ps.setInt(4, hredQueued);
            ps.setString(5, success ? "Y" : "N");
            ps.setString(6, fit(message, 1000));
            ps.setString(7, fit(uploadUser, 100));
            ps.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save upload history", ex);
        }
    }

    @Transactional(readOnly = true)
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<UploadHistoryRecord> getRecentUploadHistories(int limit) {
        int safeLimit = limit <= 0 ? 12 : Math.min(limit, 200);

        String sql = "SELECT EVENT_TIME, IMPORTED_COUNT, HSBC_QUEUED, HRED_QUEUED, SUCCESS_FLAG, MESSAGE, UPLOAD_USER"
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
                r.setUploadUser(rs.getString(7));
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
            insertRecv(conn, smtpId, email, customer.getFirstName(), appName);
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

    private void insertRecv(Connection conn, BigDecimal smtpId, String email, String name, String appName) throws Exception {
        String sql = "INSERT INTO " + properties.getQueueRecvTable()
            + " (SMTP_ID, APP_NAME, RECEIVER, RECEIVER_NAME) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, smtpId);
            ps.setString(2, fit(appName, 20));
            ps.setString(3, fit(email, 255));
            ps.setString(4, fit(name, 100));
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

    private Connection openConnection() throws Exception {
        return dataSource.getConnection();
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
