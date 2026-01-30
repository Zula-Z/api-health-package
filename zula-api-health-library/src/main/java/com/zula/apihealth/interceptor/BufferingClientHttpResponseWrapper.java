package com.zula.apihealth.interceptor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Simple buffering wrapper so response bodies can be read multiple times.
 */
public class BufferingClientHttpResponseWrapper implements ClientHttpResponse {
    private final ClientHttpResponse response;
    private final byte[] body;

    public BufferingClientHttpResponseWrapper(ClientHttpResponse response) throws IOException {
        this.response = response;
        this.body = StreamUtils.copyToByteArray(response.getBody());
    }

    @Override
    public HttpStatus getStatusCode() throws IOException {
        return response.getStatusCode();
    }

    @Override
    public int getRawStatusCode() throws IOException {
        return response.getRawStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return response.getStatusText();
    }

    @Override
    public void close() {
        response.close();
    }

    @Override
    public InputStream getBody() {
        return new ByteArrayInputStream(body);
    }

    @Override
    public HttpHeaders getHeaders() {
        return response.getHeaders();
    }

    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
