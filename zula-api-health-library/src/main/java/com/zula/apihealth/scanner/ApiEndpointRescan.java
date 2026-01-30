package com.zula.apihealth.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;

/**
 * Safety net: after the context is fully refreshed, re-run the scanner across all beans
 * so we don't miss endpoints due to early BeanPostProcessor ordering.
 */
public class ApiEndpointRescan implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger log = LoggerFactory.getLogger(ApiEndpointRescan.class);

    private final ApiEndpointScanner scanner;
    private final Environment environment;

    public ApiEndpointRescan(ApiEndpointScanner scanner, Environment environment) {
        this.scanner = scanner;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.debug("ApiEndpointRescan: context refreshed, rescanning beans for @TrackApiEndpoint");
        event.getApplicationContext().getBeansOfType(Object.class).forEach((name, bean) -> {
            try {
                scanner.postProcessAfterInitialization(bean, name);
            } catch (Exception e) {
                log.debug("ApiEndpointRescan skipped bean {} due to {}", name, e.getMessage());
            }
        });
    }
}
