package com.zula.apihealth.config;

import com.zula.apihealth.controller.ApiCallLogController;
import com.zula.apihealth.controller.ApiHealthController;
import com.zula.apihealth.interceptor.ApiCallLoggingInterceptor;
import com.zula.apihealth.repository.ApiHealthRepository;
import com.zula.apihealth.scanner.ApiEndpointScanner;
import com.zula.apihealth.service.ApiHealthService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;

@AutoConfiguration
@EnableConfigurationProperties(ApiHealthProperties.class)
@ConditionalOnClass({JdbcTemplate.class, RestTemplate.class})
public class ApiHealthAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public ApiHealthSchemaInitializer apiHealthSchemaInitializer(JdbcTemplate jdbcTemplate,
                                                                 ApiHealthProperties properties) {
        return new ApiHealthSchemaInitializer(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiHealthRepository apiHealthRepository(JdbcTemplate jdbcTemplate,
                                                   ApiHealthProperties properties) {
        return new ApiHealthRepository(jdbcTemplate, properties);
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
    public ApiCallLogController apiCallLogController(ApiHealthService service) {
        return new ApiCallLogController(service);
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
    @ConditionalOnMissingBean(name = "apiHealthRestTemplateCustomizer")
    public RestTemplateCustomizer apiHealthRestTemplateCustomizer(ApiCallLoggingInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }
}
