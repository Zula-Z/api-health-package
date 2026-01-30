package com.zula.apihealth.repository;

import com.zula.apihealth.config.ApiHealthProperties;
import com.zula.apihealth.model.ApiCallLogEntry;
import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiLogView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ApiHealthRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ApiHealthProperties properties;
    private final String schema;

    public ApiHealthRepository(JdbcTemplate jdbcTemplate, ApiHealthProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        String raw = properties.getSchemaName() != null && !properties.getSchemaName().isBlank()
                ? properties.getSchemaName()
                : "api_health";
        this.schema = com.zula.apihealth.config.ApiHealthSchemaInitializer.sanitize(raw);
    }

    public void registerEndpointIfAbsent(String name, String path, String method, String description,
                                         Integer pingIntervalSec, Boolean activeMonitor) {
        jdbcTemplate.update("INSERT INTO " + schema + ".api_endpoint_registry " +
                        "(name, path, http_method, description, ping_interval_sec, active_monitor) " +
                        "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                        "name=VALUES(name), description=VALUES(description), " +
                        "ping_interval_sec=VALUES(ping_interval_sec), active_monitor=VALUES(active_monitor)",
                name, path, method, description, pingIntervalSec, activeMonitor);
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
                entry.getId().toString(),
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

    public List<ApiEndpointView> endpointsNeedingPing(long nowEpochSeconds) {
        String sql = "SELECT r.id, r.name, r.path, r.http_method, r.description, " +
                "r.ping_interval_sec, r.active_monitor, r.last_check_time, r.last_check_status, r.last_check_success, r.last_check_body, " +
                "0 AS total_calls, 0 AS success_calls, 0 AS failure_calls, 0 AS avg_duration_ms, r.last_check_time AS last_called " +
                "FROM " + schema + ".api_endpoint_registry r " +
                "WHERE r.active_monitor = TRUE AND r.ping_interval_sec > 0 " +
                "AND (r.last_check_time IS NULL OR UNIX_TIMESTAMP(r.last_check_time) <= ? - r.ping_interval_sec)";
        return jdbcTemplate.query(sql, endpointMapper, nowEpochSeconds);
    }

    public void updateMonitorStatus(long id, int status, boolean success, String body, OffsetDateTime checkedAt) {
        jdbcTemplate.update("UPDATE " + schema + ".api_endpoint_registry SET " +
                        "last_check_status=?, last_check_success=?, last_check_body=?, last_check_time=? WHERE id=?",
                status, success, body, checkedAt, id);
    }
}
