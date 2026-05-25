package com.hsbc.sel.emgr.config;

import com.hsbc.sel.emgr.service.PfsBatchService;
import com.hsbc.sel.emgr.service.PfsEmailQueueService;
import com.hsbc.sel.emgr.service.PfsStorageService;
import com.hsbc.sel.emgr.service.PfsTemplateService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class PfsConfig {

    @Bean
    public PfsProperties pfsProperties() {
        return PfsProperties.loadDefault();
    }

    @Bean(destroyMethod = "close")
    public HikariDataSource pfsDataSource(PfsProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getDbUrl());
        config.setDriverClassName(properties.getDbDriverClass());
        config.setUsername(properties.getDbUser());
        config.setPassword(properties.getEffectiveDbPassword());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("pfs-hikari");
        return new HikariDataSource(config);
    }

    @Bean
    public PlatformTransactionManager transactionManager(HikariDataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
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
    public PfsEmailQueueService pfsEmailQueueService(PfsProperties properties, PfsBatchService batchService,
                                                     PfsTemplateService templateService, HikariDataSource dataSource) {
        return new PfsEmailQueueService(properties, batchService, templateService, dataSource);
    }
}