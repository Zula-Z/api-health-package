package com.zula.apihealth.model;

/**
 * DTO for health view (/api/health/monitoring).
 * Focuses on status/up/down and last check metadata, not traffic totals.
 */
public class ApiHealthResponse {
    public Long id;
    public String name;
    public String path;
    public String method;
    public String description;
    public String healthStatus;
    public Boolean up;
    public java.time.OffsetDateTime lastCheckTime;
    public Integer lastCheckStatus;
    public Boolean lastCheckSuccess;
    public String lastCheckBody;
    public Integer pingIntervalSec;
    public Boolean activeMonitor;
}
