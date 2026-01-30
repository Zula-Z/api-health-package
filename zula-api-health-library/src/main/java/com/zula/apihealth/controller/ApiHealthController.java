package com.zula.apihealth.controller;

import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiLogView;
import com.zula.apihealth.service.ApiHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/health")
public class ApiHealthController {
    private final ApiHealthService service;

    public ApiHealthController(ApiHealthService service) {
        this.service = service;
    }

    @GetMapping("/endpoints")
    public List<ApiEndpointView> endpoints(@RequestParam(name = "filter", required = false) String filter) {
        return service.listEndpoints(filter);
    }

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

    @GetMapping("/logs/recent")
    public List<ApiLogView> recent(@RequestParam(name = "limit", required = false) Integer limit) {
        return service.recentLogs(limit);
    }

    @GetMapping("/logs/by-endpoint")
    public List<ApiLogView> byEndpoint(@RequestParam("url") String url,
                                       @RequestParam(name = "limit", required = false) Integer limit) {
        return service.logsByEndpoint(url, limit);
    }

    @GetMapping("/monitoring")
    public List<ApiEndpointView> monitoring(@RequestParam(name = "filter", required = false) String filter) {
        return service.listMonitors(filter);
    }

    static class EndpointWithLogs {
        public ApiEndpointView endpoint;
        public List<ApiLogView> logs;
    }
}
