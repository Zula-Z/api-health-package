package com.zula.apihealth.service;

import com.zula.apihealth.config.ApiHealthProperties;
import com.zula.apihealth.model.ApiCallLogEntry;
import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiLogView;
import com.zula.apihealth.repository.ApiHealthRepository;

import java.time.OffsetDateTime;
import java.util.List;

public class ApiHealthService {
    private final ApiHealthRepository repository;
    private final ApiHealthProperties properties;

    public ApiHealthService(ApiHealthRepository repository, ApiHealthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<ApiEndpointView> listEndpoints(String filter) {
        return repository.listEndpointsWithStats(filter);
    }

    public ApiEndpointView getEndpoint(long id) {
        return repository.getEndpointWithStats(id);
    }

    public List<ApiLogView> recentLogs(Integer limit) {
        int l = limit != null ? limit : properties.getRecentLimit();
        return repository.recentLogs(l);
    }

    public List<ApiLogView> logsByEndpoint(String url, Integer limit) {
        int l = limit != null ? limit : properties.getRecentLimit();
        return repository.logsByEndpoint(url, l);
    }

    public List<ApiEndpointView> listMonitors(String filter) {
        List<ApiEndpointView> list = repository.listMonitors(filter);
        return list;
    }

    public List<ApiLogView> logsForEndpointId(long endpointId, Integer limit) {
        int l = limit != null ? limit : properties.getRecentLimit();
        return repository.logsForEndpointId(endpointId, l);
    }

    public void registerEndpoint(String name, String path, String method, String description) {
        repository.registerEndpointIfAbsent(name, path, method, description, 0, false);
    }

    public void logCall(ApiCallLogEntry entry) {
        repository.insertLog(entry);
    }

    public List<ApiEndpointView> endpointsNeedingPing() {
        List<ApiEndpointView> all = repository.endpointsMarkedForPing();
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

    public void updateMonitorStatus(long id, int status, boolean success, String body, OffsetDateTime checkedAt) {
        repository.updateMonitorStatus(id, status, success, body, checkedAt);
    }
}
