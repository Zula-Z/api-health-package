package com.zula.apihealth.controller;

import com.zula.apihealth.model.ApiLogView;
import com.zula.apihealth.service.ApiHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class ApiCallLogController {

    private final ApiHealthService service;

    public ApiCallLogController(ApiHealthService service) {
        this.service = service;
    }

    @GetMapping("/recent")
    public List<ApiLogView> getRecent(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return service.recentLogs(limit);
    }

    @GetMapping("/failed")
    public List<ApiLogView> getFailed() {
        // derive failed = success = false
        return service.logsByEndpoint("", 1000).stream().filter(l -> Boolean.FALSE.equals(l.getSuccess())).toList();
    }

    @GetMapping("/slow")
    public List<ApiLogView> getSlow(@RequestParam(name = "thresholdMs", defaultValue = "1000") int thresholdMs) {
        return service.logsByEndpoint("", 1000).stream().filter(l -> l.getDurationMs() != null && l.getDurationMs() > thresholdMs).toList();
    }

    @GetMapping("/trace/{traceId}")
    public List<ApiLogView> getByTraceId(@PathVariable String traceId) {
        return service.logsByEndpoint("", 1000).stream().filter(l -> traceId.equals(l.getTraceId())).toList();
    }

    @GetMapping("/stats/avg-duration")
    public Double getAvgDuration(@RequestParam(name = "urlPattern") String urlPattern) {
        return service.logsByEndpoint(urlPattern, 1000).stream()
                .mapToInt(l -> l.getDurationMs() == null ? 0 : l.getDurationMs())
                .average()
                .orElse(0);
    }
}
