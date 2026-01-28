package com.zula.apihealth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface TrackApiEndpoint {
    String path();
    String method() default "GET";
    String name() default "";
    String description() default "";
}
