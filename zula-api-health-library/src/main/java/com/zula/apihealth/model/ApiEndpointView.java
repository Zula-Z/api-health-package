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
    private Integer pingIntervalSec;
    private Boolean activeMonitor;
    private OffsetDateTime lastCheckTime;
    private Integer lastCheckStatus;
    private Boolean lastCheckSuccess;
    private String lastCheckBody;

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

    public Integer getPingIntervalSec() { return pingIntervalSec; }
    public void setPingIntervalSec(Integer pingIntervalSec) { this.pingIntervalSec = pingIntervalSec; }

    public Boolean getActiveMonitor() { return activeMonitor; }
    public void setActiveMonitor(Boolean activeMonitor) { this.activeMonitor = activeMonitor; }

    public OffsetDateTime getLastCheckTime() { return lastCheckTime; }
    public void setLastCheckTime(OffsetDateTime lastCheckTime) { this.lastCheckTime = lastCheckTime; }

    public Integer getLastCheckStatus() { return lastCheckStatus; }
    public void setLastCheckStatus(Integer lastCheckStatus) { this.lastCheckStatus = lastCheckStatus; }

    public Boolean getLastCheckSuccess() { return lastCheckSuccess; }
    public void setLastCheckSuccess(Boolean lastCheckSuccess) { this.lastCheckSuccess = lastCheckSuccess; }

    public String getLastCheckBody() { return lastCheckBody; }
    public void setLastCheckBody(String lastCheckBody) { this.lastCheckBody = lastCheckBody; }
}
