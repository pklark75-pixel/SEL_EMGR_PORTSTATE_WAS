package com.hsbc.sel.emgr.model;

public class DashboardStats {

    private int pendingCount;
    private int sentCount;
    private int totalQueueCount;
    private int todayUploadCount;

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getSentCount() { return sentCount; }
    public void setSentCount(int sentCount) { this.sentCount = sentCount; }

    public int getTotalQueueCount() { return totalQueueCount; }
    public void setTotalQueueCount(int totalQueueCount) { this.totalQueueCount = totalQueueCount; }

    public int getTodayUploadCount() { return todayUploadCount; }
    public void setTodayUploadCount(int todayUploadCount) { this.todayUploadCount = todayUploadCount; }
}
