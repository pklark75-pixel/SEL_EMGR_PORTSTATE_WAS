package com.hsbc.sel.emgr.model;

public class QueueSummary {

    private int hsbcQueuedCount;
    private int hredQueuedCount;

    // DB verification counts from queue tables
    private int filesTableCount;
    private int recvTableCount;
    private int infoTableCount;

    // SMTP_ID range used for verification
    private long minSmtpId;
    private long maxSmtpId;

    public int getHsbcQueuedCount() {
        return hsbcQueuedCount;
    }

    public void setHsbcQueuedCount(int hsbcQueuedCount) {
        this.hsbcQueuedCount = hsbcQueuedCount;
    }

    public int getHredQueuedCount() {
        return hredQueuedCount;
    }

    public void setHredQueuedCount(int hredQueuedCount) {
        this.hredQueuedCount = hredQueuedCount;
    }

    public int getTotalQueuedCount() {
        return hsbcQueuedCount + hredQueuedCount;
    }

    public int getFilesTableCount() {
        return filesTableCount;
    }

    public void setFilesTableCount(int filesTableCount) {
        this.filesTableCount = filesTableCount;
    }

    public int getRecvTableCount() {
        return recvTableCount;
    }

    public void setRecvTableCount(int recvTableCount) {
        this.recvTableCount = recvTableCount;
    }

    public int getInfoTableCount() {
        return infoTableCount;
    }

    public void setInfoTableCount(int infoTableCount) {
        this.infoTableCount = infoTableCount;
    }

    public long getMinSmtpId() {
        return minSmtpId;
    }

    public void setMinSmtpId(long minSmtpId) {
        this.minSmtpId = minSmtpId;
    }

    public long getMaxSmtpId() {
        return maxSmtpId;
    }

    public void setMaxSmtpId(long maxSmtpId) {
        this.maxSmtpId = maxSmtpId;
    }
}
