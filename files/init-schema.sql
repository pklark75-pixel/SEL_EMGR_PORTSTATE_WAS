-- PostgreSQL 초기 스키마 (DB2 LOAN 스키마 대체)
CREATE SCHEMA IF NOT EXISTS loan;

CREATE TABLE IF NOT EXISTS loan.emgr_smtp_files_q (
    smtp_id   BIGINT       NOT NULL,
    app_name  VARCHAR(20)  NOT NULL,
    name      VARCHAR(255) NOT NULL,
    datafile  BYTEA,
    PRIMARY KEY (smtp_id, app_name, name)
);

CREATE TABLE IF NOT EXISTS loan.emgr_smtp_recv_list_q (
    smtp_id       BIGINT       NOT NULL,
    app_name      VARCHAR(20)  NOT NULL,
    receiver      VARCHAR(255) NOT NULL,
    receiver_name VARCHAR(100),
    PRIMARY KEY (smtp_id, app_name, receiver)
);

CREATE TABLE IF NOT EXISTS loan.emgr_smtp_info_q (
    smtp_id    BIGINT        NOT NULL,
    app_name   VARCHAR(20)   NOT NULL,
    ref_no     VARCHAR(50),
    sender     VARCHAR(255),
    title      VARCHAR(255),
    content    TEXT,
    c_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    s_time     TIMESTAMP,
    s_cnt      INTEGER       NOT NULL DEFAULT 0,
    s_flag     CHAR(1)       NOT NULL DEFAULT 'N',
    source_app VARCHAR(10),
    fund_count INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (smtp_id, app_name)
);

CREATE TABLE IF NOT EXISTS loan.emgr_upload_audit (
    audit_id       BIGINT        NOT NULL,
    event_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    imported_count INTEGER       NOT NULL,
    hsbc_queued    INTEGER       NOT NULL,
    hred_queued    INTEGER       NOT NULL,
    success_flag   CHAR(1)       NOT NULL,
    message        VARCHAR(1000),
    upload_user    VARCHAR(100),
    PRIMARY KEY (audit_id)
);

CREATE INDEX IF NOT EXISTS idx_emgr_upload_audit_01
    ON loan.emgr_upload_audit (event_time DESC, audit_id DESC);

-- 기존 DB 마이그레이션 (컬럼이 없는 경우에만 실행)
ALTER TABLE loan.emgr_smtp_recv_list_q  ADD COLUMN IF NOT EXISTS receiver_name VARCHAR(100);
ALTER TABLE loan.emgr_upload_audit      ADD COLUMN IF NOT EXISTS upload_user   VARCHAR(100);
