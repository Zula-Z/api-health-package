package com.zula.apihealth.controller;

import com.zula.apihealth.model.ApiLogView;
import com.zula.apihealth.service.ApiHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints focused on exposing raw log data.
 * Note: some handlers derive subsets in-memory for simplicity.
 */
@RestController
@RequestMapping("/api/logs")
public class ApiCallLogController {

    private final ApiHealthService service;

    public ApiCallLogController(ApiHealthService service) {
        this.service = service;
    }

    @GetMapping("/recent")
    /** Recent logs with configurable limit. */
    public List<ApiLogView> getRecent(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return service.recentLogs(limit);
    }

    @GetMapping("/failed")
    /** Logs where success==false (derived). */
    public List<ApiLogView> getFailed() {
        return service.logsByEndpoint("", 1000).stream().filter(l -> Boolean.FALSE.equals(l.getSuccess())).toList();
    }

    @GetMapping("/slow")
    /** Logs above a duration threshold (derived). */
    public List<ApiLogView> getSlow(@RequestParam(name = "thresholdMs", defaultValue = "1000") int thresholdMs) {
        return service.logsByEndpoint("", 1000).stream().filter(l -> l.getDurationMs() != null && l.getDurationMs() > thresholdMs).toList();
    }

    @GetMapping("/trace/{traceId}")
    /** Logs matching a specific traceId (derived). */
    public List<ApiLogView> getByTraceId(@PathVariable String traceId) {
        return service.logsByEndpoint("", 1000).stream().filter(l -> traceId.equals(l.getTraceId())).toList();
    }

    @GetMapping("/stats/avg-duration")
    /** Average duration for a URL pattern (derived). */
    public Double getAvgDuration(@RequestParam(name = "urlPattern") String urlPattern) {
        return service.logsByEndpoint(urlPattern, 1000).stream()
                .mapToInt(l -> l.getDurationMs() == null ? 0 : l.getDurationMs())
                .average()
                .orElse(0);
    }
}
