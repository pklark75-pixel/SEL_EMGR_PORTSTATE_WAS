package com.hsbc.sel.emgr.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.BatchValidationResult;

class PfsBatchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void validateBatchFiles_successWithFourFiles() throws IOException {
        PfsBatchService service = buildService();

        write("KRHSBC_UT_20220128_1_CustAttr.txt", "C1|x|KRHSBC_UT_1|FundA|");
        write("KRHSBC_UT_20220128_1_EmailCustMast.txt", "C1|a@a.com|Hong|");
        write("KRHRED_UT_20220128_1_CustAttr.txt", "C2|x|KRHRED_UT_1|FundB|");
        write("KRHRED_UT_20220128_1_EmailCustMast.txt", "C2|b@b.com|Kim|");

        BatchValidationResult result = service.validateBatchFiles();
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateBatchFiles_failWhenDatesDifferent() throws IOException {
        PfsBatchService service = buildService();

        write("KRHSBC_UT_20220128_1_CustAttr.txt", "C1|x|KRHSBC_UT_1|FundA|");
        write("KRHSBC_UT_20220128_1_EmailCustMast.txt", "C1|a@a.com|Hong|");
        write("KRHRED_UT_20220129_1_CustAttr.txt", "C2|x|KRHRED_UT_1|FundB|");
        write("KRHRED_UT_20220129_1_EmailCustMast.txt", "C2|b@b.com|Kim|");

        BatchValidationResult result = service.validateBatchFiles();
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    private PfsBatchService buildService() {
        PfsProperties properties = new PfsProperties();
        properties.setBaseDir(tempDir.resolve("files").toString());

        PfsStorageService storage = new PfsStorageService(properties);
        PfsTemplateService template = new PfsTemplateService(properties);
        return new PfsBatchService(properties, storage, template);
    }

    private void write(String fileName, String content) throws IOException {
        Path path = tempDir.resolve("files").resolve("batch").resolve(fileName);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}

