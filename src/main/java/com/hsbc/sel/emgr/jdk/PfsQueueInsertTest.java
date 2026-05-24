package com.hsbc.sel.emgr.jdk;

import com.hsbc.sel.emgr.config.PfsProperties;
import com.hsbc.sel.emgr.model.QueueSummary;
import com.hsbc.sel.emgr.service.PfsBatchService;
import com.hsbc.sel.emgr.service.PfsEmailQueueService;
import com.hsbc.sel.emgr.service.PfsStorageService;
import com.hsbc.sel.emgr.service.PfsTemplateService;

public final class PfsQueueInsertTest {

    private PfsQueueInsertTest() {
    }

    public static void main(String[] args) {
        try {
            PfsProperties props = PfsProperties.loadDefault();
            PfsStorageService storage = new PfsStorageService(props);
            PfsTemplateService template = new PfsTemplateService(props);
            PfsBatchService batch = new PfsBatchService(props, storage, template);
            PfsEmailQueueService queue = new PfsEmailQueueService(props, batch, template);

            QueueSummary summary = queue.queueEmailsFromGeneratedHtml();
            System.out.println("Queue SUCCESS: HSBC=" + summary.getHsbcQueuedCount() + ", HRED=" + summary.getHredQueuedCount());
        } catch (Exception ex) {
            System.out.println("Queue FAILED: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}

