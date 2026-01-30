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
    private final boolean postgres;

    public ApiHealthSchemaInitializer(JdbcTemplate jdbcTemplate,
                                      ApiHealthProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.postgres = detectPostgres(jdbcTemplate);
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

        if (postgres) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + schema + ".api_call_logs (" +
                    "id CHAR(36) PRIMARY KEY," +
                    "\"timestamp\" TIMESTAMP NOT NULL," +
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
        } else {
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
        }

        if (postgres) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + schema + ".api_endpoint_registry (" +
                    "id BIGSERIAL PRIMARY KEY," +
                    "name VARCHAR(200)," +
                    "path TEXT NOT NULL," +
                    "http_method VARCHAR(10) NOT NULL," +
                    "description TEXT," +
                    "ping_interval_sec INT DEFAULT 0," +
                    "active_monitor BOOLEAN DEFAULT FALSE," +
                    "last_check_time TIMESTAMP NULL," +
                    "last_check_status INT NULL," +
                    "last_check_success BOOLEAN NULL," +
                    "last_check_body TEXT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(path, http_method)" +
                    ")");
        } else {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + schema + ".api_endpoint_registry (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "name VARCHAR(200)," +
                    "path TEXT NOT NULL," +
                    "http_method VARCHAR(10) NOT NULL," +
                    "description TEXT," +
                    "ping_interval_sec INT DEFAULT 0," +
                    "active_monitor BOOLEAN DEFAULT FALSE," +
                    "last_check_time DATETIME NULL," +
                    "last_check_status INT NULL," +
                    "last_check_success BOOLEAN NULL," +
                    "last_check_body TEXT NULL," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(path(255), http_method)" +
                    ")");
        }

        // Best-effort schema evolution for existing installs that predate new columns
        addColumnIfMissing(schema, "api_endpoint_registry", "ping_interval_sec INT DEFAULT 0");
        addColumnIfMissing(schema, "api_endpoint_registry", "active_monitor BOOLEAN DEFAULT FALSE");
        addColumnIfMissing(schema, "api_endpoint_registry", postgres ? "last_check_time TIMESTAMP NULL" : "last_check_time DATETIME NULL");
        addColumnIfMissing(schema, "api_endpoint_registry", "last_check_status INT NULL");
        addColumnIfMissing(schema, "api_endpoint_registry", "last_check_success BOOLEAN NULL");
        addColumnIfMissing(schema, "api_endpoint_registry", "last_check_body TEXT NULL");
    }

    private void addColumnIfMissing(String schema, String table, String columnDef) {
        String sql = "ALTER TABLE " + schema + "." + table + " ADD COLUMN " + columnDef;
        try {
            jdbcTemplate.execute(sql);
            log.info("Added column {} to {}.{}", columnDef.split(" ")[0], schema, table);
        } catch (Exception e) {
            // Column likely exists; swallow to stay non-breaking
            log.debug("Column add skipped for {}.{} -> {} : {}", schema, table, columnDef, e.getMessage());
        }
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

    private boolean detectPostgres(JdbcTemplate jdbcTemplate) {
        try {
            String product = jdbcTemplate.getDataSource() != null
                    ? jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName()
                    : "";
            return product != null && product.toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }
}
