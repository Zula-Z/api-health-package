package com.zula.apihealth.controller;

import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiEndpointStatsResponse;
import com.zula.apihealth.model.ApiHealthResponse;
import com.zula.apihealth.model.ApiLogView;
import com.zula.apihealth.service.ApiHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints to query registered external APIs, their logs, and monitoring status.
 */
@RestController
@RequestMapping("/admin/health")
@Slf4j
public class ApiHealthController {
    private final ApiHealthService service;

    public ApiHealthController(ApiHealthService service) {
        this.service = service;
    }

    /** List endpoints with aggregated stats; optional filter, date/time range, sort, and status filter. */
    @GetMapping("/endpoints")
    public List<ApiEndpointStatsResponse> endpoints(@RequestParam(name = "filter", required = false) String filter,
                                                    @RequestParam(name = "from", required = false) String from,
                                                    @RequestParam(name = "to", required = false) String to,
                                                    @RequestParam(name = "sort", required = false) String sort,
                                                    @RequestParam(name = "desc", defaultValue = "false") boolean desc,
                                                    @RequestParam(name = "status", required = false) String statusCsv) {
        return service.listEndpoints(filter, from, to, sort, desc, parseStatuses(statusCsv), null)
                .stream().map(this::toStatsDto).toList();
    }

    /** Get one endpoint; optionally include recent logs. */
    @GetMapping("/endpoints/{id}")
    public ResponseEntity<?> endpoint(@PathVariable long id,
                                      @RequestParam(name = "logs", defaultValue = "false") boolean includeLogs,
                                      @RequestParam(name = "limit", required = false) Integer limit) {
        ApiEndpointView view = service.getEndpoint(id);
        if (view == null) {
            return ResponseEntity.notFound().build();
        }
        if (includeLogs) {
            EndpointWithLogs payload = new EndpointWithLogs();
            payload.endpoint = view;
            payload.logs = service.logsForEndpointId(id, limit);
            return ResponseEntity.ok(payload);
        }
        return ResponseEntity.ok(view);
    }

    /** Recent logs (global). */
    @GetMapping("/logs/recent")
    public List<ApiLogView> recent(@RequestParam(name = "limit", required = false) Integer limit) {
        return service.recentLogs(limit);
    }

    /** Recent logs filtered by URL prefix. */
    @GetMapping("/logs/by-endpoint")
    public List<ApiLogView> byEndpoint(@RequestParam("url") String url,
                                       @RequestParam(name = "limit", required = false) Integer limit) {
        return service.logsByEndpoint(url, limit);
    }

    /** Health view for all endpoints; shows UP/DOWN/UNKNOWN and last check fields. */
    @GetMapping("/monitoring")
    public List<ApiHealthResponse> monitoring(@RequestParam(name = "filter", required = false) String filter,
                                              @RequestParam(name = "from", required = false) String from,
                                              @RequestParam(name = "to", required = false) String to,
                                              @RequestParam(name = "sort", required = false) String sort,
                                              @RequestParam(name = "desc", defaultValue = "false") boolean desc,
                                              @RequestParam(name = "status", required = false) String statusCsv,
                                              @RequestParam(name = "active", required = false) Boolean onlyActive) {
        log.debug("Monitoring endpoint hit with filter={}", filter);
        List<ApiEndpointView> list = service.listHealth(filter, from, to, sort, desc, parseStatuses(statusCsv), onlyActive);
        log.debug("Monitoring result size={}", list.size());
        return list.stream().map(this::toHealthDto).toList();
    }

    private List<Integer> parseStatuses(String csv) {
        if (csv == null || csv.isBlank()) return null;
        String[] parts = csv.split(",");
        List<Integer> out = new java.util.ArrayList<>();
        for (String p : parts) {
            try {
                out.add(Integer.parseInt(p.trim()));
            } catch (NumberFormatException ignored) { }
        }
        return out.isEmpty() ? null : out;
    }

    private ApiEndpointStatsResponse toStatsDto(ApiEndpointView v) {
        ApiEndpointStatsResponse dto = new ApiEndpointStatsResponse();
        dto.id = v.getId();
        dto.name = v.getName();
        dto.path = v.getPath();
        dto.method = v.getMethod();
        dto.description = v.getDescription();
        dto.totalCalls = v.getTotalCalls();
        dto.successCalls = v.getSuccessCalls();
        dto.failureCalls = v.getFailureCalls();
        dto.avgDurationMs = v.getAvgDurationMs();
        dto.lastCalled = v.getLastCalled();
        return dto;
    }

    private ApiHealthResponse toHealthDto(ApiEndpointView v) {
        ApiHealthResponse dto = new ApiHealthResponse();
        dto.id = v.getId();
        dto.name = v.getName();
        dto.path = v.getPath();
        dto.method = v.getMethod();
        dto.description = v.getDescription();
        dto.healthStatus = v.getHealthStatus();
        dto.up = v.getUp();
        dto.lastCheckTime = v.getLastCheckTime();
        dto.lastCheckStatus = v.getLastCheckStatus();
        dto.lastCheckSuccess = v.getLastCheckSuccess();
        dto.lastCheckBody = v.getLastCheckBody();
        dto.pingIntervalSec = v.getPingIntervalSec();
        dto.activeMonitor = v.getActiveMonitor();
        return dto;
    }

    static class EndpointWithLogs {
        public ApiEndpointView endpoint;
        public List<ApiLogView> logs;
    }
}
