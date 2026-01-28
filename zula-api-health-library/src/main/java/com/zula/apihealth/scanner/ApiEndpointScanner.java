package com.zula.apihealth.scanner;

import com.zula.apihealth.annotation.TrackApiEndpoint;
import com.zula.apihealth.repository.ApiHealthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Scans beans for @TrackApiEndpoint and registers them into the registry table.
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
        if (!AnnotatedElementUtils.hasAnnotation(targetClass, RestController.class)) {
            return bean;
        }

        Map<Method, TrackApiEndpoint> annotated = MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<TrackApiEndpoint>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, TrackApiEndpoint.class));

        annotated.forEach((method, ann) -> {
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
