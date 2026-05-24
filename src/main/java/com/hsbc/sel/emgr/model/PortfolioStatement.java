package com.hsbc.sel.emgr.model;

public class PortfolioStatement {

    private static final String DEFAULT_VAL = "N/A";

    private String account = DEFAULT_VAL;
    private String fundName = DEFAULT_VAL;
    private String unit = DEFAULT_VAL;
    private String openingDate = DEFAULT_VAL;
    private String invPeriod = DEFAULT_VAL;
    private String reportDate = DEFAULT_VAL;
    private String invPrincipal = DEFAULT_VAL;
    private String redBeforeCost = DEFAULT_VAL;
    private String redAfterCost = DEFAULT_VAL;
    private String fees = DEFAULT_VAL;
    private String salesFee = DEFAULT_VAL;
    private String allFee = DEFAULT_VAL;
    private String currentNAV = DEFAULT_VAL;
    private String deductableFees = DEFAULT_VAL;
    private String netRedemption = DEFAULT_VAL;
    private String cumReturnBeforeCost = DEFAULT_VAL;
    private String cumReturnAfterCost = DEFAULT_VAL;
    private String netReturnBeforeCost = DEFAULT_VAL;
    private String netReturnAfterCost = DEFAULT_VAL;
    private String currency = DEFAULT_VAL;

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getOpeningDate() { return openingDate; }
    public void setOpeningDate(String openingDate) { this.openingDate = openingDate; }
    public String getInvPeriod() { return invPeriod; }
    public void setInvPeriod(String invPeriod) { this.invPeriod = invPeriod; }
    public String getReportDate() { return reportDate; }
    public void setReportDate(String reportDate) { this.reportDate = reportDate; }
    public String getInvPrincipal() { return invPrincipal; }
    public void setInvPrincipal(String invPrincipal) { this.invPrincipal = invPrincipal; }
    public String getRedBeforeCost() { return redBeforeCost; }
    public void setRedBeforeCost(String redBeforeCost) { this.redBeforeCost = redBeforeCost; }
    public String getRedAfterCost() { return redAfterCost; }
    public void setRedAfterCost(String redAfterCost) { this.redAfterCost = redAfterCost; }
    public String getFees() { return fees; }
    public void setFees(String fees) { this.fees = fees; }
    public String getSalesFee() { return salesFee; }
    public void setSalesFee(String salesFee) { this.salesFee = salesFee; }
    public String getAllFee() { return allFee; }
    public void setAllFee(String allFee) { this.allFee = allFee; }
    public String getCurrentNAV() { return currentNAV; }
    public void setCurrentNAV(String currentNAV) { this.currentNAV = currentNAV; }
    public String getDeductableFees() { return deductableFees; }
    public void setDeductableFees(String deductableFees) { this.deductableFees = deductableFees; }
    public String getNetRedemption() { return netRedemption; }
    public void setNetRedemption(String netRedemption) { this.netRedemption = netRedemption; }
    public String getCumReturnBeforeCost() { return cumReturnBeforeCost; }
    public void setCumReturnBeforeCost(String cumReturnBeforeCost) { this.cumReturnBeforeCost = cumReturnBeforeCost; }
    public String getCumReturnAfterCost() { return cumReturnAfterCost; }
    public void setCumReturnAfterCost(String cumReturnAfterCost) { this.cumReturnAfterCost = cumReturnAfterCost; }
    public String getNetReturnBeforeCost() { return netReturnBeforeCost; }
    public void setNetReturnBeforeCost(String netReturnBeforeCost) { this.netReturnBeforeCost = netReturnBeforeCost; }
    public String getNetReturnAfterCost() { return netReturnAfterCost; }
    public void setNetReturnAfterCost(String netReturnAfterCost) { this.netReturnAfterCost = netReturnAfterCost; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}

