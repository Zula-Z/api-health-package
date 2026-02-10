package com.zula.apihealth.service;

import com.zula.apihealth.config.ApiHealthProperties;
import com.zula.apihealth.model.ApiCallLogEntry;
import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiLogDetailView;
import com.zula.apihealth.model.ApiLogView;
import com.zula.apihealth.repository.ApiHealthRepository;
import com.zula.apihealth.service.StatusClassifier;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service layer that orchestrates registry queries, log persistence, and ping scheduling needs.
 */
public class ApiHealthService {
    private final ApiHealthRepository repository;
    private final ApiHealthProperties properties;
    private final StatusClassifier classifier;

    public ApiHealthService(ApiHealthRepository repository, ApiHealthProperties properties) {
        this.repository = repository;
        this.properties = properties;
        this.classifier = new StatusClassifier(); // uses status-ranges.txt bundled with the library
    }

    /** Return all endpoints with aggregated stats; optional filter/date/sort/status and active switch. */
    public List<ApiEndpointView> listEndpoints(String filter, String from, String to, String sort, boolean desc,
                                               List<Integer> statuses, Boolean onlyActive) {
        return repository.listEndpointsWithStats(filter, from, to, sort, desc, onlyActive, statuses);
    }

    /** Fetch a single endpoint with stats by id. */
    public ApiEndpointView getEndpoint(long id) {
        return repository.getEndpointWithStats(id);
    }

    /** Recent logs limited by provided limit or default property. */
    public List<ApiLogView> recentLogs(Integer limit) {
        int l = limit != null ? limit : properties.getRecentLimit();
        return repository.recentLogs(l);
    }

    /** Recent logs filtered by URL prefix. */
    public List<ApiLogView> logsByEndpoint(String url, Integer limit) {
        int l = limit != null ? limit : properties.getRecentLimit();
        return repository.logsByEndpoint(url, l);
    }

    /** Detailed logs filtered by trace id (includes request/response headers and bodies). */
    public List<ApiLogDetailView> logDetailsByTraceId(String traceId, Integer limit) {
        int l = limit != null ? limit : 1000;
        return repository.logDetailsByTraceId(traceId, l);
    }

    /** Health view for all endpoints (both actively monitored and passive). */
    public List<ApiEndpointView> listHealth(String filter, String from, String to, String sort, boolean desc,
                                            List<Integer> statuses, Boolean onlyActive) {
        return repository.listEndpointsWithStats(filter, from, to, sort, desc, onlyActive, statuses);
    }

    /** Recent logs associated (by URL prefix) with a specific endpoint id. */
    public List<ApiLogView> logsForEndpointId(long endpointId, Integer limit) {
        int l = limit != null ? limit : properties.getRecentLimit();
        return repository.logsForEndpointId(endpointId, l);
    }

    /** Programmatic upsert of an endpoint definition without monitoring. */
    public void registerEndpoint(String name, String path, String method, String description) {
        repository.registerEndpointIfAbsent(name, path, method, description, 0, false);
    }

    /** Persist a captured API call log entry. */
    public void logCall(ApiCallLogEntry entry) {
        repository.insertLog(entry);
        // Also refresh monitor metadata based on this real call, even if active_monitor=false
        if (entry.getHttpStatus() != null) {
            boolean ok = classifier.isUp(entry.getHttpStatus());
            repository.updateMonitorStatusByUrl(entry.getUrl(), entry.getHttpMethod(), entry.getHttpStatus(), ok, entry.getResponseBody(), entry.getTimestamp());
        }
    }

    /** Endpoints marked for monitor whose interval has elapsed. */
    public List<ApiEndpointView> endpointsNeedingPing() {
        List<ApiEndpointView> all = repository.endpointsMarkedForPing();
        if (all.isEmpty()) {
            return all;
        }
        long now = java.time.Instant.now().getEpochSecond();
        return all.stream()
                .filter(e -> e.getPingIntervalSec() != null && e.getPingIntervalSec() > 0)
                .filter(e -> {
                    if (e.getLastCheckTime() == null) return true;
                    long last = e.getLastCheckTime().toEpochSecond();
                    return last + e.getPingIntervalSec() <= now;
                })
                .toList();
    }

    /** Store the result of a monitor ping. */
    public void updateMonitorStatus(long id, int status, boolean success, String body, OffsetDateTime checkedAt) {
        repository.updateMonitorStatus(id, status, success, body, checkedAt);
    }

    /** Centralized up/down decision so scheduler and interceptor share the same rules. */
    public boolean isUp(int status) {
        return classifier.isUp(status);
    }
}
