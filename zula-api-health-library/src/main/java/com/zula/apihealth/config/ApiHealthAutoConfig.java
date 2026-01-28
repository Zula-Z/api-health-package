package com.zula.apihealth.config;

import com.zula.apihealth.controller.ApiHealthController;
import com.zula.apihealth.repository.ApiHealthRepository;
import com.zula.apihealth.scanner.ApiEndpointScanner;
import com.zula.apihealth.service.ApiHealthService;
import com.zula.database.core.DatabaseManager;
import org.jdbi.v3.core.Jdbi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ApiHealthProperties.class)
@ConditionalOnClass({Jdbi.class, DatabaseManager.class})
public class ApiHealthAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public ApiHealthSchemaInitializer apiHealthSchemaInitializer(Jdbi jdbi,
                                                                 DatabaseManager databaseManager,
                                                                 ApiHealthProperties properties) {
        return new ApiHealthSchemaInitializer(jdbi, databaseManager, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiHealthRepository apiHealthRepository(Jdbi jdbi,
                                                   DatabaseManager databaseManager,
                                                   ApiHealthProperties properties) {
        return new ApiHealthRepository(jdbi, databaseManager, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiHealthService apiHealthService(ApiHealthRepository repository,
                                             ApiHealthProperties properties) {
        return new ApiHealthService(repository, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiHealthController apiHealthController(ApiHealthService service) {
        return new ApiHealthController(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiEndpointScanner apiEndpointScanner(ApiHealthRepository repository) {
        return new ApiEndpointScanner(repository);
    }
}
