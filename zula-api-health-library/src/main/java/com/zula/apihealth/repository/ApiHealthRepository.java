package com.zula.apihealth.repository;

import com.zula.apihealth.config.ApiHealthProperties;
import com.zula.apihealth.model.ApiCallLogEntry;
import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiLogView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ApiHealthRepository {
    private static final Logger log = LoggerFactory.getLogger(ApiHealthRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final ApiHealthProperties properties;
    private final String schema;
    private final boolean postgres;
    private volatile boolean tablesEnsured = false;

    public ApiHealthRepository(JdbcTemplate jdbcTemplate, ApiHealthProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        String raw = properties.getSchemaName() != null && !properties.getSchemaName().isBlank()
                ? properties.getSchemaName()
                : "api_health";
        this.schema = com.zula.apihealth.config.ApiHealthSchemaInitializer.sanitize(raw);
        this.postgres = detectPostgres(jdbcTemplate);
    }

    public void registerEndpointIfAbsent(String name, String path, String method, String description,
                                         Integer pingIntervalSec, Boolean activeMonitor) {
        String sql = insertSql();
        try {
            jdbcTemplate.update(sql, name, path, method, description, pingIntervalSec, activeMonitor);
            return;
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            // Likely missing new monitor columns; attempt to add them and retry once.
            addMonitorColumns();
            jdbcTemplate.update(sql, name, path, method, description, pingIntervalSec, activeMonitor);
            return;
        } catch (org.springframework.dao.DataAccessException ex) {
            // Fallback for table/schema missing: create everything and retry once
            if (isMissingTable(ex)) {
                ensureTables();
                jdbcTemplate.update(sql, name, path, method, description, pingIntervalSec, activeMonitor);
            } else {
                // Try once after adding columns too
                addMonitorColumns();
                jdbcTemplate.update(sql, name, path, method, description, pingIntervalSec, activeMonitor);
            }
        }
    }

    public List<ApiEndpointView> listEndpointsWithStats(String filter) {
        String base = "SELECT r.id, r.name, r.path, r.http_method, r.description, " +
                "r.ping_interval_sec, r.active_monitor, r.last_check_time, r.last_check_status, r.last_check_success, r.last_check_body, " +
                "COALESCE(COUNT(l.id),0) AS total_calls, " +
                "COALESCE(SUM(CASE WHEN l.success THEN 1 ELSE 0 END),0) AS success_calls, " +
                "COALESCE(SUM(CASE WHEN NOT l.success THEN 1 ELSE 0 END),0) AS failure_calls, " +
                "COALESCE(AVG(l.duration_ms),0) AS avg_duration_ms, " +
                "MAX(l.timestamp) AS last_called " +
                "FROM " + schema + ".api_endpoint_registry r " +
                "LEFT JOIN " + schema + ".api_call_logs l ON l.url LIKE CONCAT(r.path, '%') ";

        String where = "";
        Object[] params = new Object[]{};
        if (filter != null && !filter.isBlank()) {
            where = "WHERE r.path LIKE CONCAT('%', ?, '%') OR r.name LIKE CONCAT('%', ?, '%') ";
            params = new Object[]{filter, filter};
        }
        String tail = "GROUP BY r.id, r.name, r.path, r.http_method, r.description ORDER BY r.id";
        String sql = base + where + tail;
        return jdbcTemplate.query(sql, params, endpointMapper);
    }

    public List<ApiEndpointView> listMonitors(String filter) {
        String base = "SELECT r.id, r.name, r.path, r.http_method, r.description, " +
                "r.ping_interval_sec, r.active_monitor, r.last_check_time, r.last_check_status, r.last_check_success, r.last_check_body, " +
                "COALESCE(COUNT(l.id),0) AS total_calls, " +
                "COALESCE(SUM(CASE WHEN l.success THEN 1 ELSE 0 END),0) AS success_calls, " +
                "COALESCE(SUM(CASE WHEN NOT l.success THEN 1 ELSE 0 END),0) AS failure_calls, " +
                "COALESCE(AVG(l.duration_ms),0) AS avg_duration_ms, " +
                "MAX(l.timestamp) AS last_called " +
                "FROM " + schema + ".api_endpoint_registry r " +
                "LEFT JOIN " + schema + ".api_call_logs l ON l.url LIKE CONCAT(r.path, '%') " +
                "WHERE r.active_monitor = TRUE ";

        Object[] params = new Object[]{};
        if (filter != null && !filter.isBlank()) {
            base += "AND (r.path LIKE CONCAT('%', ?, '%') OR r.name LIKE CONCAT('%', ?, '%')) ";
            params = new Object[]{filter, filter};
        }
        String tail = "GROUP BY r.id, r.name, r.path, r.http_method, r.description ORDER BY r.id";
        List<ApiEndpointView> list = jdbcTemplate.query(base + tail, params, endpointMapper);
        if (log.isDebugEnabled()) {
            log.debug("listMonitors filter='{}' -> {} rows", filter, list.size());
        }
        return list;
    }

    public ApiEndpointView getEndpointWithStats(long id) {
        String sql = "SELECT r.id, r.name, r.path, r.http_method, r.description, " +
                "r.ping_interval_sec, r.active_monitor, r.last_check_time, r.last_check_status, r.last_check_success, r.last_check_body, " +
                "COALESCE(COUNT(l.id),0) AS total_calls, " +
                "COALESCE(SUM(CASE WHEN l.success THEN 1 ELSE 0 END),0) AS success_calls, " +
                "COALESCE(SUM(CASE WHEN NOT l.success THEN 1 ELSE 0 END),0) AS failure_calls, " +
                "COALESCE(AVG(l.duration_ms),0) AS avg_duration_ms, " +
                "MAX(l.timestamp) AS last_called " +
                "FROM " + schema + ".api_endpoint_registry r " +
                "LEFT JOIN " + schema + ".api_call_logs l ON l.url LIKE CONCAT(r.path, '%') " +
                "WHERE r.id = ? " +
                "GROUP BY r.id, r.name, r.path, r.http_method, r.description";
        List<ApiEndpointView> list = jdbcTemplate.query(sql, endpointMapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<ApiLogView> recentLogs(int limit) {
        String sql = "SELECT id, timestamp, url, http_method, http_status, duration_ms, success, trace_id " +
                "FROM " + schema + ".api_call_logs ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, logMapper, limit);
    }

    public List<ApiLogView> logsByEndpoint(String endpointLike, int limit) {
        String sql = "SELECT id, timestamp, url, http_method, http_status, duration_ms, success, trace_id " +
                "FROM " + schema + ".api_call_logs WHERE url LIKE CONCAT(?, '%') ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, logMapper, endpointLike, limit);
    }

    public List<ApiLogView> logsForEndpointId(long endpointId, int limit) {
        String sql = "SELECT l.id, l.timestamp, l.url, l.http_method, l.http_status, l.duration_ms, l.success, l.trace_id " +
                "FROM " + schema + ".api_call_logs l " +
                "JOIN " + schema + ".api_endpoint_registry r ON l.url LIKE CONCAT(r.path, '%') " +
                "WHERE r.id = ? ORDER BY l.timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, logMapper, endpointId, limit);
    }

    public void insertLog(ApiCallLogEntry entry) {
        String sql = "INSERT INTO " + schema + ".api_call_logs " +
                "(id, timestamp, url, http_method, request_headers, request_body, response_headers, response_body, http_status, duration_ms, trace_id, success, error_message) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.update(sql,
                entry.getId(), // pass UUID directly so Postgres UUID columns work
                entry.getTimestamp(),
                entry.getUrl(),
                entry.getHttpMethod(),
                entry.getRequestHeaders(),
                entry.getRequestBody(),
                entry.getResponseHeaders(),
                entry.getResponseBody(),
                entry.getHttpStatus(),
                entry.getDurationMs(),
                entry.getTraceId(),
                entry.getSuccess(),
                entry.getErrorMessage());
    }

    private final RowMapper<ApiEndpointView> endpointMapper = new RowMapper<ApiEndpointView>() {
        @Override
        public ApiEndpointView mapRow(ResultSet rs, int rowNum) throws SQLException {
            ApiEndpointView v = new ApiEndpointView();
            v.setId(rs.getLong("id"));
            v.setName(rs.getString("name"));
            v.setPath(rs.getString("path"));
            v.setMethod(rs.getString("http_method"));
            v.setDescription(rs.getString("description"));
            v.setPingIntervalSec((Integer) rs.getObject("ping_interval_sec"));
            v.setActiveMonitor((Boolean) rs.getObject("active_monitor"));
            v.setLastCheckTime(rs.getTimestamp("last_check_time") != null ? rs.getTimestamp("last_check_time").toInstant().atOffset(java.time.ZoneOffset.UTC) : null);
            v.setLastCheckStatus((Integer) rs.getObject("last_check_status"));
            v.setLastCheckSuccess((Boolean) rs.getObject("last_check_success"));
            v.setLastCheckBody(rs.getString("last_check_body"));
            v.setTotalCalls(rs.getLong("total_calls"));
            v.setSuccessCalls(rs.getLong("success_calls"));
            v.setFailureCalls(rs.getLong("failure_calls"));
            v.setAvgDurationMs(rs.getDouble("avg_duration_ms"));
            v.setLastCalled(rs.getTimestamp("last_called") != null ? rs.getTimestamp("last_called").toInstant().atOffset(java.time.ZoneOffset.UTC) : null);
            return v;
        }
    };

    private final RowMapper<ApiLogView> logMapper = new RowMapper<ApiLogView>() {
        @Override
        public ApiLogView mapRow(ResultSet rs, int rowNum) throws SQLException {
            ApiLogView v = new ApiLogView();
            v.setId(UUID.fromString(rs.getString("id")));
            v.setTimestamp(rs.getTimestamp("timestamp").toInstant().atOffset(java.time.ZoneOffset.UTC));
            v.setUrl(rs.getString("url"));
            v.setHttpMethod(rs.getString("http_method"));
            v.setHttpStatus(rs.getObject("http_status") == null ? null : rs.getInt("http_status"));
            v.setDurationMs(rs.getObject("duration_ms") == null ? null : rs.getInt("duration_ms"));
            v.setSuccess(rs.getObject("success") == null ? null : rs.getBoolean("success"));
            v.setTraceId(rs.getString("trace_id"));
            return v;
        }
    };

    public List<ApiEndpointView> endpointsMarkedForPing() {
        String sql = "SELECT r.id, r.name, r.path, r.http_method, r.description, " +
                "r.ping_interval_sec, r.active_monitor, r.last_check_time, r.last_check_status, r.last_check_success, r.last_check_body, " +
                "0 AS total_calls, 0 AS success_calls, 0 AS failure_calls, 0 AS avg_duration_ms, r.last_check_time AS last_called " +
                "FROM " + schema + ".api_endpoint_registry r " +
                "WHERE r.active_monitor = TRUE AND r.ping_interval_sec > 0";
        return jdbcTemplate.query(sql, endpointMapper);
    }

    public void updateMonitorStatus(long id, int status, boolean success, String body, OffsetDateTime checkedAt) {
        jdbcTemplate.update("UPDATE " + schema + ".api_endpoint_registry SET " +
                        "last_check_status=?, last_check_success=?, last_check_body=?, last_check_time=? WHERE id=?",
                status, success, body, checkedAt, id);
    }

    private String insertSql() {
        if (postgres) {
            return "INSERT INTO " + schema + ".api_endpoint_registry " +
                    "(name, path, http_method, description, ping_interval_sec, active_monitor) " +
                    "VALUES (?,?,?,?,?,?) ON CONFLICT (path, http_method) DO UPDATE SET " +
                    "name=EXCLUDED.name, description=EXCLUDED.description, " +
                    "ping_interval_sec=EXCLUDED.ping_interval_sec, active_monitor=EXCLUDED.active_monitor";
        }
        return "INSERT INTO " + schema + ".api_endpoint_registry " +
                "(name, path, http_method, description, ping_interval_sec, active_monitor) " +
                "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                "name=VALUES(name), description=VALUES(description), " +
                "ping_interval_sec=VALUES(ping_interval_sec), active_monitor=VALUES(active_monitor)";
    }

    private void addMonitorColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE " + schema + ".api_endpoint_registry ADD COLUMN IF NOT EXISTS ping_interval_sec INT DEFAULT 0");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE " + schema + ".api_endpoint_registry ADD COLUMN IF NOT EXISTS active_monitor BOOLEAN DEFAULT FALSE");
        } catch (Exception ignored) {}
        try {
            String ts = postgres ? "TIMESTAMP" : "DATETIME";
            jdbcTemplate.execute("ALTER TABLE " + schema + ".api_endpoint_registry ADD COLUMN IF NOT EXISTS last_check_time " + ts + " NULL");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE " + schema + ".api_endpoint_registry ADD COLUMN IF NOT EXISTS last_check_status INT NULL");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE " + schema + ".api_endpoint_registry ADD COLUMN IF NOT EXISTS last_check_success BOOLEAN NULL");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE " + schema + ".api_endpoint_registry ADD COLUMN IF NOT EXISTS last_check_body TEXT NULL");
        } catch (Exception ignored) {}
    }

    private void ensureTables() {
        if (tablesEnsured) return;
        synchronized (this) {
            if (tablesEnsured) return;
            try {
                if (postgres) {
                    jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
                    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + schema + ".api_call_logs (" +
                            "id UUID PRIMARY KEY," +
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
                tablesEnsured = true;
                log.info("ApiHealth tables ensured in schema {}", schema);
            } catch (Exception e) {
                log.warn("Failed to auto-create API health tables in schema {}: {}", schema, e.getMessage());
            }
        }
    }

    private boolean isMissingTable(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) return false;
        msg = msg.toLowerCase();
        return msg.contains("doesn't exist") || msg.contains("does not exist") || msg.contains("undefined table");
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
