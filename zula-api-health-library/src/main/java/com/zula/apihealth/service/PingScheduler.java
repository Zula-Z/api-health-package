package com.zula.apihealth.service;

import com.zula.apihealth.model.ApiEndpointView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Active pinging of external endpoints marked with {@code @EndpointMonitor(active=true)}.
 * Runs on a fixed delay and respects per-endpoint intervals to avoid hammering targets.
 */
public class PingScheduler {
    private static final Logger log = LoggerFactory.getLogger(PingScheduler.class);
    private static final ZoneId ZONE_NAIROBI = ZoneId.of("Africa/Nairobi");

    private final ApiHealthService service;
    private final RestTemplate restTemplate;

    public PingScheduler(ApiHealthService service, RestTemplate restTemplate) {
        this.service = service;
        this.restTemplate = restTemplate;
    }
    /**
     *  Run every 30 seconds; internal logic respects per-endpoint interval
    */
    @Scheduled(fixedDelay = 30_000)
    public void ping() {
        List<ApiEndpointView> targets = service.endpointsNeedingPing();
        if (targets.isEmpty()) {
            log.debug("PingScheduler: no endpoints eligible for ping right now");
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZONE_NAIROBI);
        log.debug("PingScheduler: will ping {} endpoint(s)", targets.size());
        for (ApiEndpointView endpoint : targets) {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            // HEAD first
            try {
                ResponseEntity<String> resp = restTemplate.exchange(endpoint.getPath(), HttpMethod.HEAD, entity, String.class);
                int status = resp.getStatusCodeValue();
                service.updateMonitorStatus(endpoint.getId(), status, service.isUp(status), null, now);
                log.info("Pinged(HEAD) {} -> status {}", endpoint.getPath(), status);
                continue;
            } catch (RestClientResponseException headResp) {
                int status = headResp.getRawStatusCode();
                String body = headResp.getResponseBodyAsString();
                service.updateMonitorStatus(endpoint.getId(), status, service.isUp(status), truncate(body), now);
                log.info("Pinged(HEAD) {} -> status {} (error handled)", endpoint.getPath(), status);
                continue;
            } catch (RestClientException headConn) {
                log.debug("HEAD failed for {} -> {}, trying GET", endpoint.getPath(), headConn.getMessage());
            }
            // GET fallback
            try {
                ResponseEntity<String> resp = restTemplate.exchange(endpoint.getPath(), HttpMethod.GET, entity, String.class);
                int status = resp.getStatusCodeValue();
                String body = resp.getBody();
                service.updateMonitorStatus(endpoint.getId(), status, service.isUp(status), truncate(body), now);
                log.info("Pinged(GET) {} -> status {}", endpoint.getPath(), status);
            } catch (RestClientResponseException getResp) {
                int status = getResp.getRawStatusCode();
                String body = getResp.getResponseBodyAsString();
                service.updateMonitorStatus(endpoint.getId(), status, service.isUp(status), truncate(body), now);
                log.info("Pinged(GET) {} -> status {} (error handled)", endpoint.getPath(), status);
            } catch (RestClientException ex) {
                service.updateMonitorStatus(endpoint.getId(), 0, false, ex.getMessage(), now);
                log.warn("Ping failed for {}: {}", endpoint.getPath(), ex.getMessage());
            }
        }
    }

    private String truncate(String body) {
        if (body == null) return null;
        if (body.length() > 4000) {
            return body.substring(0, 4000) + "...<truncated>";
        }
        return body;
    }
}
