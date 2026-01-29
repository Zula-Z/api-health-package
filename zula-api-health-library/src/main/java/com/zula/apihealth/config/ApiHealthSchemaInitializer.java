package com.zula.apihealth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ensures the API health tables exist at startup (JDBC-based, Boot 2.7 compatible).
 */
public class ApiHealthSchemaInitializer implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ApiHealthSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final ApiHealthProperties properties;

    public ApiHealthSchemaInitializer(JdbcTemplate jdbcTemplate,
                                      ApiHealthProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isAutoCreateTables()) {
            log.info("ApiHealth auto-create disabled; skipping DDL");
            return;
        }
        String schema = resolveSchema();
        log.info("Ensuring API health tables exist in schema {}", schema);

        // Create schema/database
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + schema + ".api_call_logs (" +
                "id CHAR(36) PRIMARY KEY," +
                "`timestamp` DATETIME NOT NULL," +
                "url TEXT NOT NULL," +
                "http_method VARCHAR(10) NOT NULL," +
                "request_headers TEXT," +
                "request_body TEXT," +
                "response_headers TEXT," +
                "response_body TEXT," +
                "http_status INTEGER," +
                "duration_ms INTEGER NOT NULL," +
                "trace_id VARCHAR(64) NOT NULL," +
                "success BOOLEAN NOT NULL," +
                "error_message TEXT" +
                ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + schema + ".api_endpoint_registry (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "name VARCHAR(200)," +
                "path TEXT NOT NULL," +
                "http_method VARCHAR(10) NOT NULL," +
                "description TEXT," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE(path(255), http_method)" +
                ")");
    }

    public String resolveSchema() {
        String raw = (properties.getSchemaName() != null && !properties.getSchemaName().isBlank())
                ? properties.getSchemaName()
                : "api_health";
        return sanitize(raw);
    }

    /**
     * Replace characters invalid for identifiers with underscores.
     */
    public static String sanitize(String schema) {
        return schema == null ? "api_health" : schema.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
