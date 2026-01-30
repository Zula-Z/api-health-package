package com.zula.apihealth.service;

import com.zula.apihealth.config.ApiHealthProperties;
import com.zula.apihealth.model.ApiCallLogEntry;
import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiLogView;
import com.zula.apihealth.repository.ApiHealthRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service layer that orchestrates registry queries, log persistence, and ping scheduling needs.
 */
public class ApiHealthService {
    private final ApiHealthRepository repository;
    private final ApiHealthProperties properties;

    public ApiHealthService(ApiHealthRepository repository, ApiHealthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Return all endpoints with aggregated stats; optional filter by path or name. */
    public List<ApiEndpointView> listEndpoints(String filter) {
        return repository.listEndpointsWithStats(filter);
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

    /** Monitored endpoints only, optional filter. */
    public List<ApiEndpointView> listMonitors(String filter) {
        return repository.listMonitors(filter);
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
}
