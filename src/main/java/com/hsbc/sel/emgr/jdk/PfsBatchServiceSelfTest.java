package com.hsbc.sel.emgr.jdk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.BatchValidationResult;
import com.hsbc.sel.emgr.service.PfsBatchService;
import com.hsbc.sel.emgr.service.PfsStorageService;
import com.hsbc.sel.emgr.service.PfsTemplateService;

public final class PfsBatchServiceSelfTest {

    private PfsBatchServiceSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createTempDirectory("pfs-selftest-");

        PfsProperties properties = new PfsProperties();
        properties.setBaseDir(tempRoot.resolve("files").toString());

        PfsStorageService storage = new PfsStorageService(properties);
        PfsTemplateService template = new PfsTemplateService(properties);
        PfsBatchService service = new PfsBatchService(properties, storage, template);

        write(properties, "KRHSBC_UT_20220128_1_CustAttr.txt", "C1|x|KRHSBC_UT_1|FundA|");
        write(properties, "KRHSBC_UT_20220128_1_EmailCustMast.txt", "C1|a@a.com|Hong|");
        write(properties, "KRHRED_UT_20220128_1_CustAttr.txt", "C2|x|KRHRED_UT_1|FundB|");
        write(properties, "KRHRED_UT_20220128_1_EmailCustMast.txt", "C2|b@b.com|Kim|");

        BatchValidationResult result = service.validateBatchFiles();
        if (!result.isValid()) {
            throw new IllegalStateException("Self test failed: " + String.join(" | ", result.getErrors()));
        }

        System.out.println("SELF-TEST PASS");
    }

    private static void write(PfsProperties properties, String fileName, String content) throws Exception {
        Path target = properties.getBatchDirPath().resolve(fileName);
        Files.createDirectories(target.getParent());
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
    }
}

