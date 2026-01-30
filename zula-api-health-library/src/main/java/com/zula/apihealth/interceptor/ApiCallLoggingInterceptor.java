package com.zula.apihealth.interceptor;

import com.zula.apihealth.model.ApiCallLogEntry;
import com.zula.apihealth.service.ApiHealthService;
import com.zula.apihealth.interceptor.BufferingClientHttpResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Intercepts outbound RestTemplate calls, captures request/response, and hands off to persistence.
 * Keeps network latency on the main thread minimal; DB write is delegated to the async service.
 */
public class ApiCallLoggingInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ApiCallLoggingInterceptor.class);
    private final ApiHealthService apiHealthService;
    private final int maxBodyLength = 8000;

    public ApiCallLoggingInterceptor(ApiHealthService apiHealthService) {
        this.apiHealthService = apiHealthService;
    }

    /** Capture and log a single outbound HTTP exchange. */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        OffsetDateTime start = OffsetDateTime.now();
        String url = request.getURI().toString();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String reqHeaders = request.getHeaders().toString();
        String reqBody = truncate(new String(body, StandardCharsets.UTF_8));

        boolean success = false;
        int status = 0;
        String respHeaders = null;
        String respBody = null;
        String errorMessage = null;
        int durationMs = 0;

        try {
            ClientHttpResponse response = execution.execute(request, body);
            BufferingClientHttpResponseWrapper buffered = new BufferingClientHttpResponseWrapper(response);
            durationMs = (int) (OffsetDateTime.now().toInstant().toEpochMilli() - start.toInstant().toEpochMilli());
            status = buffered.getRawStatusCode();
            respHeaders = buffered.getHeaders().toString();
            respBody = truncate(buffered.getBodyAsString());
            success = status >= 200 && status < 400;
            return buffered;
        } catch (Exception ex) {
            durationMs = (int) (OffsetDateTime.now().toInstant().toEpochMilli() - start.toInstant().toEpochMilli());
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            ApiCallLogEntry entry = new ApiCallLogEntry();
            entry.setId(UUID.randomUUID());
            entry.setTimestamp(start);
            entry.setUrl(url);
            entry.setHttpMethod(method);
            entry.setRequestHeaders(reqHeaders);
            entry.setRequestBody(reqBody);
            entry.setResponseHeaders(respHeaders);
            entry.setResponseBody(respBody);
            entry.setHttpStatus(status);
            entry.setDurationMs(durationMs);
            entry.setTraceId(UUID.randomUUID().toString());
            entry.setSuccess(success);
            entry.setErrorMessage(errorMessage);
            apiHealthService.logCall(entry);
        }
    }

    private String truncate(String body) {
        if (body == null) return null;
        if (body.length() > maxBodyLength) {
            return body.substring(0, maxBodyLength) + "[TRUNCATED]";
        }
        return body;
    }
}
