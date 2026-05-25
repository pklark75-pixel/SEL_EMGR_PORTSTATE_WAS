package com.hsbc.sel.emgr.web;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.BatchValidationResult;
import com.hsbc.sel.emgr.model.QueueRecord;
import com.hsbc.sel.emgr.model.QueueSummary;
import com.hsbc.sel.emgr.model.UploadHistoryRecord;
import com.hsbc.sel.emgr.service.PfsBatchService;
import com.hsbc.sel.emgr.service.PfsEmailQueueService;
import com.hsbc.sel.emgr.service.PfsStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

@Controller
public class PfsController {

    private static final Logger log = LoggerFactory.getLogger(PfsController.class);

    private static final int PAGE_SIZE = 10;

    private final PfsProperties properties;
    private final PfsStorageService storageService;
    private final PfsBatchService batchService;
    private final PfsEmailQueueService queueService;

    public PfsController(PfsProperties properties, PfsStorageService storageService,
                         PfsBatchService batchService, PfsEmailQueueService queueService) {
        this.properties = properties;
        this.storageService = storageService;
        this.batchService = batchService;
        this.queueService = queueService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/pfs";
    }

    @GetMapping("/pfs")
    public String home(@RequestParam(value = "filterTime", required = false) String filterTime,
                       @RequestParam(value = "appFilter",  required = false) String appFilter,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       Model model) {

        model.addAttribute("uploadHistoryRows", properties.getUploadHistoryRows());
        model.addAttribute("filterTime", filterTime);
        model.addAttribute("appFilter", appFilter);
        model.addAttribute("page", page);
        model.addAttribute("pageSize", PAGE_SIZE);

        try {
            List<UploadHistoryRecord> histories = queueService.getRecentUploadHistories(properties.getUploadHistoryRows());
            model.addAttribute("uploadHistories", histories);
        } catch (Exception ex) {
            model.addAttribute("uploadHistories", Collections.emptyList());
            model.addAttribute("dbError", "DB 연결 실패 (이력 조회): " + ex.getMessage());
        }

        try {
            int totalCount = queueService.countQueueRecords(filterTime, appFilter);
            int totalPages = totalCount == 0 ? 1 : (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
            int safePage   = Math.min(Math.max(page, 0), totalPages - 1);

            List<QueueRecord> records = queueService.getQueueRecords(filterTime, appFilter, safePage, PAGE_SIZE);
            model.addAttribute("queueRecords", records);
            model.addAttribute("totalCount", totalCount);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("page", safePage);
        } catch (Exception ex) {
            model.addAttribute("queueRecords", Collections.emptyList());
            model.addAttribute("totalCount", 0);
            model.addAttribute("totalPages", 1);
            if (!model.containsAttribute("dbError")) {
                model.addAttribute("dbError", "DB 연결 실패 (큐 조회): " + ex.getMessage());
            }
        }

        return "pfs";
    }

    @PostMapping("/pfs/upload-zip")
    public String uploadZip(@RequestParam(value = "batchZip", required = false) MultipartFile batchZip,
                            RedirectAttributes redirect) {
        if (batchZip == null || batchZip.isEmpty()) {
            saveUploadHistorySafe(false, 0, 0, 0, "ZIP 파일 미탐지");
            redirect.addFlashAttribute("error", "ZIP 파일을 찾지 못했습니다.");
            return "redirect:/pfs";
        }

        byte[] zipBytes;
        try {
            zipBytes = batchZip.getBytes();
        } catch (Exception ex) {
            saveUploadHistorySafe(false, 0, 0, 0, "파일 읽기 실패");
            redirect.addFlashAttribute("error", "파일 읽기 실패: " + ex.getMessage());
            return "redirect:/pfs";
        }

        if (zipBytes.length > properties.getUploadZipMaxBytes()) {
            saveUploadHistorySafe(false, 0, 0, 0, "파일 크기 초과");
            redirect.addFlashAttribute("error", "ZIP 파일 크기가 허용 한도를 초과합니다.");
            return "redirect:/pfs";
        }

        try {
            storageService.clearDirectory(properties.getBatchDirPath());
            int imported = storageService.importBatchFilesFromZip(zipBytes, true);

            BatchValidationResult validation = batchService.validateBatchFiles();
            if (!validation.isValid()) {
                saveUploadHistorySafe(false, imported, 0, 0, "검증 실패");
                redirect.addFlashAttribute("error",
                    "ZIP 업로드 후 검증 실패: " + String.join(" | ", validation.getErrors()));
                return "redirect:/pfs";
            }

            QueueSummary queue = queueService.queueEmailsFromGeneratedHtml();
            saveUploadHistorySafe(true, imported, queue.getHsbcQueuedCount(), queue.getHredQueuedCount(), "성공");

            redirect.addFlashAttribute("message",
                "ZIP 업로드/큐저장 완료. imported=" + imported
                + ", reportDate=" + validation.getReportDate()
                + ", queued(HSBC/HRED)=" + queue.getHsbcQueuedCount() + "/" + queue.getHredQueuedCount()
                + ", verify(files/recv/info)=" + queue.getFilesTableCount() + "/" + queue.getRecvTableCount() + "/" + queue.getInfoTableCount()
                + ", smtpIdRange=" + queue.getMinSmtpId() + "-" + queue.getMaxSmtpId());

        } catch (Exception ex) {
            saveUploadHistorySafe(false, 0, 0, 0, "실패: " + ex.getMessage());
            redirect.addFlashAttribute("error", "ZIP 자동 처리 실패: " + ex.getMessage());
        } finally {
            storageService.clearDirectory(properties.getBatchDirPath());
        }

        return "redirect:/pfs";
    }

    @GetMapping("/pfs/html-file")
    @ResponseBody
    public ResponseEntity<byte[]> htmlFile(@RequestParam("id") long id,
                                            @RequestParam("app") String app,
                                            @RequestParam("name") String name) {
        byte[] bytes = queueService.getHtmlFileBytes(id, app, name);
        if (bytes == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(bytes);
    }

    private void saveUploadHistorySafe(boolean success, int imported, int hsbc, int hred, String message) {
        try { queueService.saveUploadHistory(success, imported, hsbc, hred, message); }
        catch (Exception ex) { log.warn("upload history save failed: {}", ex.getMessage()); }
    }
}
