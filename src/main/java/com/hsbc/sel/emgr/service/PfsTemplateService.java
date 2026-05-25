package com.hsbc.sel.emgr.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.Customer;
import com.hsbc.sel.emgr.model.PortfolioStatement;

public class PfsTemplateService {

    private final PfsProperties properties;
    private final Map<String, String[]> currencyMarker = new HashMap<String, String[]>();
    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    public PfsTemplateService(PfsProperties properties) {
        this.properties = properties;
        currencyMarker.put("KRW", new String[] {"suffix", " 원"});
        currencyMarker.put("EUR", new String[] {"prefix", "EUR "});
        currencyMarker.put("JPY", new String[] {"prefix", "JPY "});
        currencyMarker.put("USD", new String[] {"prefix", "USD "});
    }

    public String renderHtml(Customer customer, boolean isDbk) {
        String type = isDbk ? "HRED" : "HSBC";
        String header = readTemplate("header.htm")
            .replace("<%[firstName]%>", safe(customer.getFirstName()))
            .replace("<%[emailAddr]%>", safe(customer.getEmailAddr()));

        String bodyTemplate = readTemplate("body_" + type + ".htm");
        StringBuilder body = new StringBuilder();

        for (PortfolioStatement statement : customer.getStatements()) {
            String row = bodyTemplate;
            String[] marker = currencyMarker.get(statement.getCurrency());
            String prefix = marker != null && "prefix".equals(marker[0]) ? marker[1] : "";
            String suffix = marker != null && "suffix".equals(marker[0]) ? marker[1] : "";

            row = row.replace("<%[KR" + type + "_UT_]%>", safe(statement.getFundName()));
            row = row.replace("<%[KR" + type + "_Acc_]%>", safe(statement.getAccount()));
            row = row.replace("<%[KR" + type + "_Unit_]%>", safe(statement.getUnit()));
            row = row.replace("<%[KR" + type + "_Acc_OpenDt_]%>", safe(statement.getOpeningDate()));
            row = row.replace("<%[KR" + type + "_Inv_Period_Days_]%>", safe(statement.getInvPeriod()));
            row = row.replace("<%[KR" + type + "_UT_Statement_Date]%>", safe(statement.getReportDate()));
            row = row.replace("<%[KR" + type + "_Inv_Pri_]%>", prefix + safe(statement.getInvPrincipal()) + suffix);
            row = row.replace("<%[KR" + type + "_Red_BeforeCost_]%>", prefix + safe(statement.getRedBeforeCost()) + suffix);
            row = row.replace("<%[KR" + type + "_Red_AfterCost_]%>", prefix + safe(statement.getRedAfterCost()) + suffix);
            row = row.replace("<%[KR" + type + "_Fees_]%>", prefix + safe(statement.getFees()) + suffix);
            row = row.replace("<%[KR" + type + "_Sales_Fee_]%>", safe(statement.getSalesFee()));
            row = row.replace("<%[KR" + type + "_All_Fee_]%>", safe(statement.getAllFee()));
            row = row.replace("<%[KR" + type + "_CNAV_]%>", prefix + safe(statement.getCurrentNAV()) + suffix);
            row = row.replace("<%[KR" + type + "_Deductable_Fees_]%>", prefix + safe(statement.getDeductableFees()) + suffix);
            row = row.replace("<%[KR" + type + "_Net_Red_]%>", prefix + safe(statement.getNetRedemption()) + suffix);
            row = row.replace("<%[KR" + type + "_Cum_Return_Before_Cost_]%>", safe(statement.getCumReturnBeforeCost()));
            row = row.replace("<%[KR" + type + "_Cum_Return_After_Cost_]%>", safe(statement.getCumReturnAfterCost()));
            row = row.replace("<%[KR" + type + "_Net_Ret_Before_Cost_]%>", safe(statement.getNetReturnBeforeCost()));
            row = row.replace("<%[KR" + type + "_Net_Ret_After_Cost_]%>", safe(statement.getNetReturnAfterCost()));
            body.append(row.trim());
        }

        String footer = readTemplate("footer.htm");
        return (header + body + footer).trim();
    }

    public String readEmailContentTemplate() {
        return readTemplate("content.htm").trim();
    }

    private String readTemplate(String fileName) {
        return templateCache.computeIfAbsent(fileName, key -> {
            Path path = properties.getTemplateDirPath().resolve(key);
            try {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Template read failed: " + path, ex);
            }
        });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

