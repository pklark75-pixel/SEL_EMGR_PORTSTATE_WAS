package com.hsbc.sel.emgr.model;

public class ProcessSummary {

    private String reportDate;
    private int hsbcHtmlCount;
    private int hredHtmlCount;

    public String getReportDate() { return reportDate; }
    public void setReportDate(String reportDate) { this.reportDate = reportDate; }
    public int getHsbcHtmlCount() { return hsbcHtmlCount; }
    public void setHsbcHtmlCount(int hsbcHtmlCount) { this.hsbcHtmlCount = hsbcHtmlCount; }
    public int getHredHtmlCount() { return hredHtmlCount; }
    public void setHredHtmlCount(int hredHtmlCount) { this.hredHtmlCount = hredHtmlCount; }
}

