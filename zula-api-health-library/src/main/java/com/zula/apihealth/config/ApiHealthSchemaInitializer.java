package com.zula.apihealth.config;

import com.zula.database.core.DatabaseManager;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Ensures the API health tables exist at startup.
 */
public class ApiHealthSchemaInitializer implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ApiHealthSchemaInitializer.class);

    private final Jdbi jdbi;
    private final DatabaseManager databaseManager;
    private final ApiHealthProperties properties;

    public ApiHealthSchemaInitializer(Jdbi jdbi,
                                      DatabaseManager databaseManager,
                                      ApiHealthProperties properties) {
        this.jdbi = jdbi;
        this.databaseManager = databaseManager;
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

        jdbi.useHandle(handle -> {
            handle.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

            handle.execute("CREATE TABLE IF NOT EXISTS " + schema + ".api_call_logs (" +
                    "id UUID PRIMARY KEY," +
                    "\"timestamp\" TIMESTAMPTZ NOT NULL," +
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

            handle.execute("CREATE TABLE IF NOT EXISTS " + schema + ".api_endpoint_registry (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "name VARCHAR(200)," +
                    "path TEXT NOT NULL," +
                    "http_method VARCHAR(10) NOT NULL," +
                    "description TEXT," +
                    "created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(path, http_method)" +
                    ")");

            handle.execute("CREATE INDEX IF NOT EXISTS idx_api_call_logs_ts ON " + schema + ".api_call_logs(\"timestamp\" DESC)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_api_call_logs_url ON " + schema + ".api_call_logs(url)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_api_call_logs_trace ON " + schema + ".api_call_logs(trace_id)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_api_call_logs_success ON " + schema + ".api_call_logs(success)");
        });
    }

    public String resolveSchema() {
        String raw = (properties.getSchemaName() != null && !properties.getSchemaName().isBlank())
                ? properties.getSchemaName()
                : databaseManager.generateSchemaName();
        return sanitize(raw);
    }

    /**
     * Replace characters invalid for PostgreSQL identifiers with underscores.
     */
    public static String sanitize(String schema) {
        return schema == null ? "public" : schema.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
