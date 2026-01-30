package com.zula.apihealth.controller;

import com.zula.apihealth.model.ApiEndpointView;
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
@RequestMapping("/api/health")
@Slf4j
public class ApiHealthController {
    private final ApiHealthService service;

    public ApiHealthController(ApiHealthService service) {
        this.service = service;
    }

    /** List endpoints with aggregated stats; optional filter by path/name. */
    @GetMapping("/endpoints")
    public List<ApiEndpointView> endpoints(@RequestParam(name = "filter", required = false) String filter) {
        return service.listEndpoints(filter);
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

    /** Monitored endpoints (active=true), optional filter. */
    @GetMapping("/monitoring")
    public List<ApiEndpointView> monitoring(@RequestParam(name = "filter", required = false) String filter) {
        log.debug("Monitoring endpoint hit with filter={}", filter);
        List<ApiEndpointView> list = service.listMonitors(filter);
        log.debug("Monitoring result size={}", list.size());
        return list;
    }

    static class EndpointWithLogs {
        public ApiEndpointView endpoint;
        public List<ApiLogView> logs;
    }
}
