package com.hsbc.sel.emgr.model;

import java.util.List;

public class QueuePage {

    private final List<QueueRecord> records;
    private final int totalCount;
    private final int totalPages;
    private final int page;

    public QueuePage(List<QueueRecord> records, int totalCount, int totalPages, int page) {
        this.records = records;
        this.totalCount = totalCount;
        this.totalPages = totalPages;
        this.page = page;
    }

    public List<QueueRecord> getRecords()  { return records; }
    public int getTotalCount()             { return totalCount; }
    public int getTotalPages()             { return totalPages; }
    public int getPage()                   { return page; }
}
