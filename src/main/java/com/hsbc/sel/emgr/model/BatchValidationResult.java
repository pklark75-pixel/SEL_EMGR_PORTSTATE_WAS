package com.hsbc.sel.emgr.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BatchValidationResult {

    private boolean valid;
    private String reportDate;
    private final List<String> errors = new ArrayList<String>();
    private final Map<String, Path> matchedFiles = new LinkedHashMap<String, Path>();

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getReportDate() { return reportDate; }
    public void setReportDate(String reportDate) { this.reportDate = reportDate; }
    public List<String> getErrors() { return errors; }
    public Map<String, Path> getMatchedFiles() { return matchedFiles; }
}

