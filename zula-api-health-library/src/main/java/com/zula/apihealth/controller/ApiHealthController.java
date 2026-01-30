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

    /** List endpoints with aggregated stats; optional filter, date/time range, sort, and status filter. */
    @GetMapping("/endpoints")
    public List<ApiEndpointView> endpoints(@RequestParam(name = "filter", required = false) String filter,
                                           @RequestParam(name = "from", required = false) String from,
                                           @RequestParam(name = "to", required = false) String to,
                                           @RequestParam(name = "sort", required = false) String sort,
                                           @RequestParam(name = "desc", defaultValue = "false") boolean desc,
                                           @RequestParam(name = "status", required = false) String statusCsv) {
        return service.listEndpoints(filter, from, to, sort, desc, parseStatuses(statusCsv), null);
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
    public List<ApiEndpointView> monitoring(@RequestParam(name = "filter", required = false) String filter,
                                            @RequestParam(name = "from", required = false) String from,
                                            @RequestParam(name = "to", required = false) String to,
                                            @RequestParam(name = "sort", required = false) String sort,
                                            @RequestParam(name = "desc", defaultValue = "false") boolean desc,
                                            @RequestParam(name = "status", required = false) String statusCsv,
                                            @RequestParam(name = "active", required = false) Boolean onlyActive) {
        log.debug("Monitoring endpoint hit with filter={}", filter);
        List<ApiEndpointView> list = service.listHealth(filter, from, to, sort, desc, parseStatuses(statusCsv), onlyActive);
        log.debug("Monitoring result size={}", list.size());
        return list;
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

    static class EndpointWithLogs {
        public ApiEndpointView endpoint;
        public List<ApiLogView> logs;
    }
}
