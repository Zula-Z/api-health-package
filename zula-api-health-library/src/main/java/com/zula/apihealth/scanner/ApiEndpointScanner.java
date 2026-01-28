package com.zula.apihealth.scanner;

import com.zula.apihealth.annotation.TrackApiEndpoint;
import com.zula.apihealth.repository.ApiHealthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Scans ALL beans for @TrackApiEndpoint and registers them into the registry table.
 * This lets you annotate service/client classes, not only controllers.
 */
public class ApiEndpointScanner implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(ApiEndpointScanner.class);

    private final ApiHealthRepository repository;

    public ApiEndpointScanner(ApiHealthRepository repository) {
        this.repository = repository;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();

        Map<Method, TrackApiEndpoint> annotated = MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<TrackApiEndpoint>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, TrackApiEndpoint.class));

        annotated.forEach((method, ann) -> {
            if (ann.path() == null || ann.path().isBlank()) {
                log.warn("Skipping @TrackApiEndpoint with blank path on {}#{}", beanName, method.getName());
                return;
            }
            repository.registerEndpointIfAbsent(
                    ann.name(),
                    ann.path(),
                    ann.method(),
                    ann.description()
            );
            log.info("Registered tracked API endpoint {} {} ({})", ann.method(), ann.path(), beanName + "#" + method.getName());
        });

        return bean;
    }
}
