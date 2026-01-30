package com.zula.apihealth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation to enable active health pinging for an external API endpoint.
 * Apply alongside {@link TrackApiEndpoint} on the same method.
 *
 * - active=true flags it for PingScheduler
 * - pingIntervalSeconds sets how often (in seconds) to allow a ping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface EndpointMonitor {
    /**
     * How often (in seconds) to ping the endpoint. 0 disables active pinging.
     */
    int pingIntervalSeconds() default 0;

    /**
     * Whether active monitoring is enabled for this endpoint.
     */
    boolean active() default false;
}
