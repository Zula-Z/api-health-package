package com.zula.apihealth.model;

/**
 * DTO for call/statistics view (/api/health/endpoints).
 * Focuses on traffic numbers and last call, not health check details.
 */
public class ApiEndpointStatsResponse {
    public Long id;
    public String name;
    public String path;
    public String method;
    public String description;
    public Long totalCalls;
    public Long successCalls;
    public Long failureCalls;
    public Double avgDurationMs;
    public java.time.OffsetDateTime lastCalled;
}
