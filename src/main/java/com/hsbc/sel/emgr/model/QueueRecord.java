package com.hsbc.sel.emgr.model;

public class QueueRecord {

    private long smtpId;
    private String appName;
    private String sourceApp;
    private String refNo;
    private String receiver;
    private String receiverName;
    private String sendFlag;
    private String createdTime;
    private int fundCount;
    private long fileSize;

    public long getSmtpId() { return smtpId; }
    public void setSmtpId(long smtpId) { this.smtpId = smtpId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getSourceApp() { return sourceApp; }
    public void setSourceApp(String sourceApp) { this.sourceApp = sourceApp; }

    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getSendFlag() { return sendFlag; }
    public void setSendFlag(String sendFlag) { this.sendFlag = sendFlag; }

    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }

    public int getFundCount() { return fundCount; }
    public void setFundCount(int fundCount) { this.fundCount = fundCount; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
}

