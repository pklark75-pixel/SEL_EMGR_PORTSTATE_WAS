package com.hsbc.sel.emgr.jdk;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

import com.hsbc.sel.emgr.config.PfsProperties;

/**
 * Test program for EMGR_UPLOAD_AUDIT table
 * Tests:
 * 1. DB connection
 * 2. Insert sample audit record
 * 3. Query the audit records
 */
public final class PfsUploadAuditTest {

    private PfsUploadAuditTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== PFS Upload Audit Test ===\n");

        PfsProperties props = PfsProperties.loadDefault();

        System.out.println("[Step 1] Loading DB configuration...");
        System.out.println("URL   : " + props.getDbUrl());
        System.out.println("User  : " + props.getDbUser());
        System.out.println("Audit Table : " + props.getUploadAuditTable());

        String password;
        try {
            password = props.getEffectiveDbPassword();
            System.out.println("Key   : resolved OK");
        } catch (Exception ex) {
            System.out.println("Key resolution FAILED: " + ex.getMessage());
            return;
        }

        System.out.println("\n[Step 2] Loading DB driver...");
        System.out.println("Driver: " + props.getDbDriverClass());
        try {
            Class.forName(props.getDbDriverClass());
            System.out.println("Driver loaded OK");
        } catch (Exception ex) {
            System.out.println("Driver loading FAILED: " + ex.getMessage());
            return;
        }

        System.out.println("\n[Step 3] Connecting to database...");
        try (Connection conn = DriverManager.getConnection(props.getDbUrl(), props.getDbUser(), password)) {
            System.out.println("Connection OK (autoCommit=" + conn.getAutoCommit() + ")");

            // Test 1: Check table structure
            System.out.println("\n[Step 4] Checking table structure...");
            checkTableStructure(conn, props.getUploadAuditTable());

            // Test 2: Insert sample audit record
            System.out.println("\n[Step 5] Inserting sample audit record...");
            long auditId = insertSampleAudit(conn, props.getUploadAuditTable());
            System.out.println("Insert OK (AUDIT_ID=" + auditId + ")");

            // Test 3: Query recent audit records
            System.out.println("\n[Step 6] Querying recent audit records...");
            queryAuditRecords(conn, props.getUploadAuditTable());

            System.out.println("\n=== TEST PASSED ===");
        } catch (Exception ex) {
            System.out.println("Connection FAILED: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void checkTableStructure(Connection conn, String tableName) throws Exception {
        String sql = "SELECT COLNAME, COLTYPE FROM SYSCAT.COLUMNS WHERE TABNAME = 'EMGR_UPLOAD_AUDIT' AND TABSCHEMA = 'LOAN' ORDER BY COLNO";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("Table structure:");
            while (rs.next()) {
                String colName = rs.getString(1);
                String colType = rs.getString(2);
                System.out.println("  - " + colName + " : " + colType);
            }
        }
    }

    private static long insertSampleAudit(Connection conn, String tableName) throws Exception {
        long auditId = System.currentTimeMillis() * 1000L;

        String sql = "INSERT INTO " + tableName
            + " (AUDIT_ID, EVENT_TIME, IMPORTED_COUNT, HSBC_QUEUED, HRED_QUEUED, SUCCESS_FLAG, MESSAGE)"
            + " VALUES (?, CURRENT TIMESTAMP, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, auditId);
            ps.setInt(2, 4);           // IMPORTED_COUNT
            ps.setInt(3, 1500);        // HSBC_QUEUED
            ps.setInt(4, 1200);        // HRED_QUEUED
            ps.setString(5, "Y");      // SUCCESS_FLAG
            ps.setString(6, "Test upload completed successfully");  // MESSAGE
            ps.executeUpdate();
        }

        return auditId;
    }

    private static void queryAuditRecords(Connection conn, String tableName) throws Exception {
        String sql = "SELECT AUDIT_ID, EVENT_TIME, IMPORTED_COUNT, HSBC_QUEUED, HRED_QUEUED, SUCCESS_FLAG, MESSAGE"
            + " FROM " + tableName
            + " ORDER BY EVENT_TIME DESC, AUDIT_ID DESC"
            + " FETCH FIRST 10 ROWS ONLY";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("\nRecent 10 audit records:");
            System.out.println(String.format(
                "%-20s %-26s %-8s %-8s %-8s %-8s %-30s",
                "AUDIT_ID", "EVENT_TIME", "IMPORT", "HSBC", "HRED", "SUCCESS", "MESSAGE"
            ));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 130; i++) {
                sb.append("-");
            }
            System.out.println(sb.toString());

            int count = 0;
            while (rs.next()) {
                long auditId = rs.getLong(1);
                Timestamp eventTime = rs.getTimestamp(2);
                int importedCount = rs.getInt(3);
                int hsbcQueued = rs.getInt(4);
                int hredQueued = rs.getInt(5);
                String successFlag = rs.getString(6);
                String message = rs.getString(7);

                String msgShort = (message != null && message.length() > 30)
                    ? message.substring(0, 27) + "..."
                    : message;

                System.out.println(String.format(
                    "%-20d %-26s %-8d %-8d %-8d %-8s %-30s",
                    auditId, eventTime, importedCount, hsbcQueued, hredQueued, successFlag, msgShort
                ));
                count++;
            }

            System.out.println("Total records: " + count);
        }
    }
}

