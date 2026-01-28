package com.zula.apihealth.service;

import com.zula.apihealth.config.ApiHealthProperties;
import com.zula.apihealth.model.ApiCallLogEntry;
import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiLogView;
import com.zula.apihealth.repository.ApiHealthRepository;

import java.util.List;

public class ApiHealthService {
    private final ApiHealthRepository repository;
    private final ApiHealthProperties properties;

    public ApiHealthService(ApiHealthRepository repository, ApiHealthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<ApiEndpointView> listEndpoints() {
        return repository.listEndpointsWithStats();
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

    public List<ApiLogView> logsForEndpointId(long endpointId, Integer limit) {
        int l = limit != null ? limit : properties.getRecentLimit();
        return repository.logsForEndpointId(endpointId, l);
    }

    public void registerEndpoint(String name, String path, String method, String description) {
        repository.registerEndpointIfAbsent(name, path, method, description);
    }

    public void logCall(ApiCallLogEntry entry) {
        repository.insertLog(entry);
    }
}
