package com.hsbc.sel.emgr.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.BatchValidationResult;
import com.hsbc.sel.emgr.model.Customer;
import com.hsbc.sel.emgr.model.EmailCustomerRecord;
import com.hsbc.sel.emgr.model.PortfolioStatement;
import com.hsbc.sel.emgr.model.ProcessSummary;

public class PfsBatchService {

    private static final Pattern FILE_PATTERN = Pattern.compile("^(KRHSBC|KRHRED)_UT_(\\d{8})_1_(CustAttr|CustSubs|EmailCustMast)\\.txt$");

    private final PfsProperties properties;
    private final PfsStorageService storageService;
    private final PfsTemplateService templateService;

    public PfsBatchService(PfsProperties properties, PfsStorageService storageService, PfsTemplateService templateService) {
        this.properties = properties;
        this.storageService = storageService;
        this.templateService = templateService;
    }

    public BatchValidationResult validateBatchFiles() {
        BatchValidationResult result = new BatchValidationResult();
        List<String> names = storageService.listFileNames(properties.getBatchDirPath());

        if (names.size() != 4 && names.size() != 6) {
            result.getErrors().add("Batch file count must be 4 or 6. current=" + names.size());
            result.setValid(false);
            return result;
        }

        String reportDate = null;
        Map<String, Path> matched = result.getMatchedFiles();

        for (String fileName : names) {
            Matcher matcher = FILE_PATTERN.matcher(fileName);
            if (!matcher.matches()) {
                result.getErrors().add("Invalid batch filename: " + fileName);
                continue;
            }

            String prefix = matcher.group(1);
            String date = matcher.group(2);
            String suffix = matcher.group(3);

            if (reportDate == null) {
                reportDate = date;
            } else if (!reportDate.equals(date)) {
                result.getErrors().add("All batch files must have the same report date.");
            }

            String key = prefix + suffix;
            Path filePath = properties.getBatchDirPath().resolve(fileName);
            matched.put(key, filePath);
        }

        String[] required = new String[] {
            "KRHSBCCustAttr", "KRHSBCEmailCustMast", "KRHREDCustAttr", "KRHREDEmailCustMast"
        };

        for (String key : required) {
            if (!matched.containsKey(key)) {
                result.getErrors().add("Missing required file for " + key);
            }
        }

        result.setReportDate(reportDate);
        result.setValid(result.getErrors().isEmpty());
        return result;
    }

    public ProcessSummary processBatchFiles() {
        BatchValidationResult validation = validateBatchFiles();
        if (!validation.isValid()) {
            throw new IllegalArgumentException(String.join("; ", validation.getErrors()));
        }

        storageService.clearDirectory(properties.getHsbcHtmlDirPath());
        storageService.clearDirectory(properties.getHredHtmlDirPath());

        ProcessSummary summary = new ProcessSummary();
        summary.setReportDate(validation.getReportDate());

        List<Customer> hsbcCustomers = parseCustomers(
            validation.getMatchedFiles().get("KRHSBCEmailCustMast"),
            validation.getMatchedFiles().get("KRHSBCCustAttr"),
            false
        );
        writeHtmlFiles(hsbcCustomers, properties.getHsbcHtmlDirPath(), false);
        summary.setHsbcHtmlCount(hsbcCustomers.size());

        List<Customer> hredCustomers = parseCustomers(
            validation.getMatchedFiles().get("KRHREDEmailCustMast"),
            validation.getMatchedFiles().get("KRHREDCustAttr"),
            true
        );
        writeHtmlFiles(hredCustomers, properties.getHredHtmlDirPath(), true);
        summary.setHredHtmlCount(hredCustomers.size());

        return summary;
    }

    public List<Customer> loadCustomers(BatchValidationResult validation, boolean isDbk) {
        if (validation == null || !validation.isValid()) {
            throw new IllegalArgumentException("Batch validation is required before loading customers.");
        }

        if (isDbk) {
            return parseCustomers(
                validation.getMatchedFiles().get("KRHREDEmailCustMast"),
                validation.getMatchedFiles().get("KRHREDCustAttr"),
                true
            );
        }

        return parseCustomers(
            validation.getMatchedFiles().get("KRHSBCEmailCustMast"),
            validation.getMatchedFiles().get("KRHSBCCustAttr"),
            false
        );
    }

    private List<Customer> parseCustomers(Path emailCustMastFile, Path custAttrFile, boolean isDbk) {
        String header = isDbk ? "KRHRED" : "KRHSBC";

        List<EmailCustomerRecord> emailRecords = parseEmailRecords(emailCustMastFile);
        List<String[]> attributes = parseAttributeRecords(custAttrFile);

        Map<String, List<String[]>> attributeByCustomer = new LinkedHashMap<String, List<String[]>>();
        for (String[] attr : attributes) {
            if (attr.length < 4) {
                continue;
            }
            String customerNo = attr[0];
            List<String[]> list = attributeByCustomer.get(customerNo);
            if (list == null) {
                list = new ArrayList<String[]>();
                attributeByCustomer.put(customerNo, list);
            }
            list.add(attr);
        }

        List<Customer> customers = new ArrayList<Customer>();
        for (EmailCustomerRecord emailRecord : emailRecords) {
            Customer customer = new Customer(emailRecord.getCustomerNo(), emailRecord.getEmail(), emailRecord.getFirstName());
            List<String[]> attrs = attributeByCustomer.get(emailRecord.getCustomerNo());
            if (attrs != null) {
                buildStatements(customer, attrs, header);
            }
            customers.add(customer);
        }

        return customers;
    }

    private void buildStatements(Customer customer, List<String[]> attrs, String header) {
        PortfolioStatement current = null;
        String lastAccount = "N/A";

        for (String[] attr : attrs) {
            if (attr.length < 4) {
                continue;
            }

            String attrName = attr[2];
            String attrValue = attr[3];

            if (attrName.equals(header + "_UT_Statement_Date")) {
                if (current != null) {
                    current.setReportDate(attrValue);
                }
                continue;
            }

            if (attrName.startsWith(header + "_UT_")) {
                current = new PortfolioStatement();
                current.setFundName(attrValue);
                continue;
            }

            if (current == null) {
                continue;
            }

            if (attrName.startsWith(header + "_Acc_OpenDt_")) {
                current.setOpeningDate(attrValue);
            } else if (attrName.startsWith(header + "_Acc_")) {
                if ("&nbsp;".equals(attrValue)) {
                    current.setAccount(lastAccount);
                } else {
                    current.setAccount(attrValue);
                    lastAccount = attrValue;
                }
            } else if (attrName.startsWith(header + "_Unit_")) {
                current.setUnit(attrValue);
            } else if (attrName.startsWith(header + "_Inv_Period_Days_")) {
                current.setInvPeriod(attrValue);
            } else if (attrName.startsWith(header + "_Inv_Pri_")) {
                current.setInvPrincipal(attrValue);
            } else if (attrName.startsWith(header + "_Red_BeforeCost_")) {
                current.setRedBeforeCost(attrValue);
            } else if (attrName.startsWith(header + "_Red_AfterCost_")) {
                current.setRedAfterCost(attrValue);
            } else if (attrName.startsWith(header + "_Fees_")) {
                current.setFees(attrValue);
            } else if (attrName.startsWith(header + "_Sales_Fee_")) {
                current.setSalesFee(attrValue);
            } else if (attrName.startsWith(header + "_All_Fee_")) {
                current.setAllFee(attrValue);
            } else if (attrName.startsWith(header + "_CNAV_")) {
                current.setCurrentNAV(attrValue);
            } else if (attrName.startsWith(header + "_Deductable_Fees_")) {
                current.setDeductableFees(attrValue);
            } else if (attrName.startsWith(header + "_Net_Red_")) {
                current.setNetRedemption(attrValue);
            } else if (attrName.startsWith(header + "_Cum_Return_Before_Cost_")) {
                current.setCumReturnBeforeCost(attrValue);
            } else if (attrName.startsWith(header + "_Cum_Return_After_Cost_")) {
                current.setCumReturnAfterCost(attrValue);
            } else if (attrName.startsWith(header + "_Net_Ret_Before_Cost_")) {
                current.setNetReturnBeforeCost(attrValue);
            } else if (attrName.startsWith(header + "_Net_Ret_After_Cost_")) {
                current.setNetReturnAfterCost(attrValue);
            } else if (attrName.startsWith(header + "_Fund_Currency_")) {
                current.setCurrency(attrValue);
                customer.addStatement(current);
            }
        }
    }

    private void writeHtmlFiles(List<Customer> customers, Path outputDir, boolean isDbk) {
        for (Customer customer : customers) {
            String html = templateService.renderHtml(customer, isDbk);
            customer.setHtmlContent(html);
            Path outputFile = outputDir.resolve(customer.getCustNo() + ".html");
            try {
                Files.write(outputFile, html.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to write html file: " + outputFile, ex);
            }
        }
    }

    private List<EmailCustomerRecord> parseEmailRecords(Path filePath) {
        List<String> lines = readLines(filePath);
        List<EmailCustomerRecord> result = new ArrayList<EmailCustomerRecord>();

        for (String line : lines) {
            String[] split = line.split("\\|", -1);
            if (split.length < 3) {
                continue;
            }
            result.add(new EmailCustomerRecord(split[0], split[1], split[2]));
        }

        return result;
    }

    private List<String[]> parseAttributeRecords(Path filePath) {
        List<String> lines = readLines(filePath);
        List<String[]> result = new ArrayList<String[]>();

        for (String line : lines) {
            result.add(line.split("\\|", -1));
        }

        return result;
    }

    private List<String> readLines(Path filePath) {
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) {
                lines.set(0, lines.get(0).substring(1));
            }
            return lines;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file: " + filePath, ex);
        }
    }
}

