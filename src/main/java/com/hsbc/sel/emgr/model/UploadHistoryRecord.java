package com.hsbc.sel.emgr.model;

public class UploadHistoryRecord {

    private String eventTime;
    private int importedCount;
    private int hsbcQueued;
    private int hredQueued;
    private boolean success;
    private String message;
    private String uploadUser;

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public int getHsbcQueued() {
        return hsbcQueued;
    }

    public void setHsbcQueued(int hsbcQueued) {
        this.hsbcQueued = hsbcQueued;
    }

    public int getHredQueued() {
        return hredQueued;
    }

    public void setHredQueued(int hredQueued) {
        this.hredQueued = hredQueued;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUploadUser() {
        return uploadUser;
    }

    public void setUploadUser(String uploadUser) {
        this.uploadUser = uploadUser;
    }
}

