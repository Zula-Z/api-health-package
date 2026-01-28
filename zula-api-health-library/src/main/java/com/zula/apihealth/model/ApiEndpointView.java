package com.zula.apihealth.model;

import java.time.OffsetDateTime;

public class ApiEndpointView {
    private Long id;
    private String name;
    private String path;
    private String method;
    private String description;
    private Long totalCalls;
    private Long successCalls;
    private Long failureCalls;
    private Double avgDurationMs;
    private OffsetDateTime lastCalled;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(Long totalCalls) { this.totalCalls = totalCalls; }

    public Long getSuccessCalls() { return successCalls; }
    public void setSuccessCalls(Long successCalls) { this.successCalls = successCalls; }

    public Long getFailureCalls() { return failureCalls; }
    public void setFailureCalls(Long failureCalls) { this.failureCalls = failureCalls; }

    public Double getAvgDurationMs() { return avgDurationMs; }
    public void setAvgDurationMs(Double avgDurationMs) { this.avgDurationMs = avgDurationMs; }

    public OffsetDateTime getLastCalled() { return lastCalled; }
    public void setLastCalled(OffsetDateTime lastCalled) { this.lastCalled = lastCalled; }
}
