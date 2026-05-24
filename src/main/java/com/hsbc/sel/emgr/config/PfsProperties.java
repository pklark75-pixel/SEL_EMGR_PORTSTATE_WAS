package com.hsbc.sel.emgr.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Properties;
import java.io.InputStream;

import com.hsbc.sel.emgr.security.AesCryptoUtil;
import com.hsbc.sel.emgr.security.KeyStoreUtil;

public class PfsProperties {

    private String baseDir = System.getProperty("user.dir") + "\\files";
    private String batchDirName = "batch";
    private String htmlParentDirName = "html";
    private String hsbcDirName = "sel";
    private String hredDirName = "dbk";
    private String templateDirName = "template";

    private boolean dbQueueEnabled = false;
    private String dbUrl = "";
    private String dbUser = "";
    private String dbPassword = "";
    private String dbPasswordEnc = "";
    private String dbPasswordKeyEnv = "PFS_DB_KEY";

    private String dbPasswordKeyFile = "";          // db.password.keyFile
    private String dbPasswordKeyWinCred = "";       // db.password.keyWinCred
    private String dbDriverClass = "org.postgresql.Driver";

    private String queueFilesTable = "LOAN.EMGR_SMTP_FILES_Q";
    private String queueRecvTable = "LOAN.EMGR_SMTP_RECV_LIST_Q";
    private String queueInfoTable = "LOAN.EMGR_SMTP_INFO_Q";
    private String uploadAuditTable = "LOAN.EMGR_UPLOAD_AUDIT";

    private int uploadZipMaxBytes = 20 * 1024 * 1024;
    private int queueRecentRows = 20;
    private int uploadHistoryRows = 12;

    private String appName = "eManager";
    private String sender = "HSBC Korea <info@kr.hsbc.com>";
    private String title = "HSBC 은행 펀드 잔고 안내";

    public static PfsProperties loadDefault() {
        PfsProperties properties = new PfsProperties();
        // WAS/WLP 배포 시 -Dpfs.baseDir=<path> JVM 옵션으로 기본 디렉토리 오버라이드 가능
        String baseDirOverride = System.getProperty("pfs.baseDir");
        if (baseDirOverride != null && !baseDirOverride.trim().isEmpty()) {
            properties.baseDir = baseDirOverride.trim();
        }
        Path filePath = properties.getBaseDirPath().resolve("pfs-jdk.properties");
        properties.loadFromFileIfExists(filePath);
        return properties;
    }

    public void loadFromFileIfExists(Path filePath) {
        if (!Files.exists(filePath)) {
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(filePath)) {
            props.load(in);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load properties file: " + filePath, ex);
        }

        baseDir = props.getProperty("pfs.baseDir", baseDir);
        batchDirName = props.getProperty("pfs.batchDirName", batchDirName);
        htmlParentDirName = props.getProperty("pfs.htmlParentDirName", htmlParentDirName);
        hsbcDirName = props.getProperty("pfs.hsbcDirName", hsbcDirName);
        hredDirName = props.getProperty("pfs.hredDirName", hredDirName);
        templateDirName = props.getProperty("pfs.templateDirName", templateDirName);

        dbQueueEnabled = Boolean.parseBoolean(props.getProperty("db.queue.enabled", Boolean.toString(dbQueueEnabled)));
        dbUrl = props.getProperty("db.url", dbUrl);
        dbUser = props.getProperty("db.user", dbUser);
        dbPassword = props.getProperty("db.password", dbPassword);
        dbPasswordEnc = props.getProperty("db.password.enc", dbPasswordEnc);
        dbPasswordKeyEnv = props.getProperty("db.password.keyEnv", dbPasswordKeyEnv);
        dbPasswordKeyFile = props.getProperty("db.password.keyFile", dbPasswordKeyFile);
        dbPasswordKeyWinCred = props.getProperty("db.password.keyWinCred", dbPasswordKeyWinCred);
        dbDriverClass = props.getProperty("db.driverClass", dbDriverClass);

        queueFilesTable = props.getProperty("db.queue.filesTable", queueFilesTable);
        queueRecvTable = props.getProperty("db.queue.recvTable", queueRecvTable);
        queueInfoTable = props.getProperty("db.queue.infoTable", queueInfoTable);
        uploadAuditTable = props.getProperty("db.audit.uploadTable", uploadAuditTable);

        uploadZipMaxBytes = Integer.parseInt(props.getProperty("pfs.upload.zipMaxBytes", Integer.toString(uploadZipMaxBytes)));
        queueRecentRows = Integer.parseInt(props.getProperty("pfs.queue.recentRows", Integer.toString(queueRecentRows)));
        uploadHistoryRows = Integer.parseInt(props.getProperty("pfs.upload.historyRows", Integer.toString(uploadHistoryRows)));

        appName = props.getProperty("mail.appName", appName);
        sender = props.getProperty("mail.sender", sender);
        title = props.getProperty("mail.title", title);
    }

    public Path getBaseDirPath() {
        return Paths.get(baseDir);
    }

    public Path getBatchDirPath() {
        return getBaseDirPath().resolve(batchDirName);
    }

    public Path getHtmlParentDirPath() {
        return getBaseDirPath().resolve(htmlParentDirName);
    }

    public Path getHsbcHtmlDirPath() {
        return getHtmlParentDirPath().resolve(hsbcDirName);
    }

    public Path getHredHtmlDirPath() {
        return getHtmlParentDirPath().resolve(hredDirName);
    }

    public Path getTemplateDirPath() {
        return getBaseDirPath().resolve(templateDirName);
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getBatchDirName() {
        return batchDirName;
    }

    public void setBatchDirName(String batchDirName) {
        this.batchDirName = batchDirName;
    }

    public String getHtmlParentDirName() {
        return htmlParentDirName;
    }

    public void setHtmlParentDirName(String htmlParentDirName) {
        this.htmlParentDirName = htmlParentDirName;
    }

    public String getHsbcDirName() {
        return hsbcDirName;
    }

    public void setHsbcDirName(String hsbcDirName) {
        this.hsbcDirName = hsbcDirName;
    }

    public String getHredDirName() {
        return hredDirName;
    }

    public void setHredDirName(String hredDirName) {
        this.hredDirName = hredDirName;
    }

    public String getTemplateDirName() {
        return templateDirName;
    }

    public void setTemplateDirName(String templateDirName) {
        this.templateDirName = templateDirName;
    }

    public boolean isDbQueueEnabled() {
        return dbQueueEnabled;
    }

    public void setDbQueueEnabled(boolean dbQueueEnabled) {
        this.dbQueueEnabled = dbQueueEnabled;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public String getDbPasswordEnc() {
        return dbPasswordEnc;
    }

    public void setDbPasswordEnc(String dbPasswordEnc) {
        this.dbPasswordEnc = dbPasswordEnc;
    }

    public String getDbPasswordKeyEnv() {
        return dbPasswordKeyEnv;
    }

    public void setDbPasswordKeyEnv(String dbPasswordKeyEnv) {
        this.dbPasswordKeyEnv = dbPasswordKeyEnv;
    }

    public String getEffectiveDbPassword() {
        if (dbPasswordEnc != null && !dbPasswordEnc.trim().isEmpty()) {
            String key = resolveKey();
            return AesCryptoUtil.decrypt(dbPasswordEnc.trim(), key);
        }
        return dbPassword;
    }

    /**
     * 우선순위:
     * 1. 환경변수 (db.password.keyEnv, 기본 PFS_DB_KEY)
     * 2. 키 파일 (db.password.keyFile)
     * 3. Windows Credential Manager (db.password.keyWinCred)
     */
    private String resolveKey() {
        // 1. 환경변수
        String envKey = System.getenv(dbPasswordKeyEnv);
        if (envKey != null && !envKey.trim().isEmpty()) {
            System.out.println("[PFS] Key source: environment variable (" + dbPasswordKeyEnv + ")");
            return envKey.trim();
        }

        // 2. 키 파일
        if (dbPasswordKeyFile != null && !dbPasswordKeyFile.trim().isEmpty()) {
            String fileKey = KeyStoreUtil.readKeyFromFile(dbPasswordKeyFile.trim());
            System.out.println("[PFS] Key source: key file (" + dbPasswordKeyFile + ")");
            return fileKey;
        }

        // 3. Windows Credential Manager
        if (dbPasswordKeyWinCred != null && !dbPasswordKeyWinCred.trim().isEmpty()) {
            String credKey = KeyStoreUtil.readKeyFromWindowsCredential(dbPasswordKeyWinCred.trim());
            System.out.println("[PFS] Key source: Windows Credential Manager (" + dbPasswordKeyWinCred + ")");
            return credKey;
        }

        throw new IllegalStateException(
            "No key source configured for db.password.enc. " +
            "Set one of: env(" + dbPasswordKeyEnv + "), db.password.keyFile, db.password.keyWinCred"
        );
    }

    public String getDbPasswordKeyFile() {
        return dbPasswordKeyFile;
    }

    public void setDbPasswordKeyFile(String dbPasswordKeyFile) {
        this.dbPasswordKeyFile = dbPasswordKeyFile;
    }

    public String getDbPasswordKeyWinCred() {
        return dbPasswordKeyWinCred;
    }

    public void setDbPasswordKeyWinCred(String dbPasswordKeyWinCred) {
        this.dbPasswordKeyWinCred = dbPasswordKeyWinCred;
    }

    public String getDbDriverClass() {
        return dbDriverClass;
    }

    public void setDbDriverClass(String dbDriverClass) {
        this.dbDriverClass = dbDriverClass;
    }

    public String getQueueFilesTable() {
        return queueFilesTable;
    }

    public void setQueueFilesTable(String queueFilesTable) {
        this.queueFilesTable = queueFilesTable;
    }

    public String getQueueRecvTable() {
        return queueRecvTable;
    }

    public void setQueueRecvTable(String queueRecvTable) {
        this.queueRecvTable = queueRecvTable;
    }

    public String getQueueInfoTable() {
        return queueInfoTable;
    }

    public void setQueueInfoTable(String queueInfoTable) {
        this.queueInfoTable = queueInfoTable;
    }

    public String getUploadAuditTable() {
        return uploadAuditTable;
    }

    public void setUploadAuditTable(String uploadAuditTable) {
        this.uploadAuditTable = uploadAuditTable;
    }

    public int getUploadZipMaxBytes() {
        return uploadZipMaxBytes;
    }

    public void setUploadZipMaxBytes(int uploadZipMaxBytes) {
        this.uploadZipMaxBytes = uploadZipMaxBytes;
    }

    public int getQueueRecentRows() {
        return queueRecentRows;
    }

    public void setQueueRecentRows(int queueRecentRows) {
        this.queueRecentRows = queueRecentRows;
    }

    public int getUploadHistoryRows() {
        return uploadHistoryRows;
    }

    public void setUploadHistoryRows(int uploadHistoryRows) {
        this.uploadHistoryRows = uploadHistoryRows;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
