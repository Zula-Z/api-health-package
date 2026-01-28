package com.zula.apihealth.config;

import com.zula.apihealth.controller.ApiHealthController;
import com.zula.apihealth.controller.ApiCallLogController;
import com.zula.apihealth.interceptor.ApiCallLoggingInterceptor;
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
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

    @Bean
    @ConditionalOnMissingBean
    public ApiCallLoggingInterceptor apiCallLoggingInterceptor(ApiHealthService service) {
        return new ApiCallLoggingInterceptor(service);
    }

    @Bean
    @ConditionalOnMissingBean(name = "apiHealthTaskExecutor")
    public TaskExecutor apiHealthTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("api-health-");
        exec.initialize();
        return exec;
    }

    @Bean
    @ConditionalOnMissingBean(name = "apiHealthRestTemplateCustomizer")
    public RestTemplateCustomizer apiHealthRestTemplateCustomizer(ApiCallLoggingInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiCallLogController apiCallLogController(ApiHealthService service) {
        return new ApiCallLogController(service);
    }
}
