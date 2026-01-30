package com.zula.apihealth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a client or controller method whose outbound call should be registered
 * in the endpoint registry and have its calls logged.
 *
 * Required: {@code path}; defaults: method=GET, name="", description="".
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface TrackApiEndpoint {
    String path();
    String method() default "GET";
    String name() default "";
    String description() default "";
}
