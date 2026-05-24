package com.hsbc.sel.emgr.config;

import com.hsbc.sel.emgr.service.PfsBatchService;
import com.hsbc.sel.emgr.service.PfsEmailQueueService;
import com.hsbc.sel.emgr.service.PfsStorageService;
import com.hsbc.sel.emgr.service.PfsTemplateService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PfsConfig {

    @Bean
    public PfsProperties pfsProperties() {
        return PfsProperties.loadDefault();
    }

    @Bean
    public PfsStorageService pfsStorageService(PfsProperties properties) {
        return new PfsStorageService(properties);
    }

    @Bean
    public PfsTemplateService pfsTemplateService(PfsProperties properties) {
        return new PfsTemplateService(properties);
    }

    @Bean
    public PfsBatchService pfsBatchService(PfsProperties properties, PfsStorageService storageService, PfsTemplateService templateService) {
        return new PfsBatchService(properties, storageService, templateService);
    }

    @Bean
    public PfsEmailQueueService pfsEmailQueueService(PfsProperties properties, PfsBatchService batchService, PfsTemplateService templateService) {
        return new PfsEmailQueueService(properties, batchService, templateService);
    }
}