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

    public void registerEndpointIfAbsent(String name, String path, String method, String description) {
        jdbcTemplate.update("INSERT IGNORE INTO " + schema + ".api_endpoint_registry (name, path, http_method, description) VALUES (?,?,?,?)",
                name, path, method, description);
    }

    public List<ApiEndpointView> listEndpointsWithStats() {
        String sql = "SELECT r.id, r.name, r.path, r.http_method, r.description, " +
                "COALESCE(COUNT(l.id),0) AS total_calls, " +
                "COALESCE(SUM(CASE WHEN l.success THEN 1 ELSE 0 END),0) AS success_calls, " +
                "COALESCE(SUM(CASE WHEN NOT l.success THEN 1 ELSE 0 END),0) AS failure_calls, " +
                "COALESCE(AVG(l.duration_ms),0) AS avg_duration_ms, " +
                "MAX(l.timestamp) AS last_called " +
                "FROM " + schema + ".api_endpoint_registry r " +
                "LEFT JOIN " + schema + ".api_call_logs l ON l.url LIKE CONCAT(r.path, '%') " +
                "GROUP BY r.id, r.name, r.path, r.http_method, r.description " +
                "ORDER BY r.id";
        return jdbcTemplate.query(sql, endpointMapper);
    }

    public ApiEndpointView getEndpointWithStats(long id) {
        String sql = "SELECT r.id, r.name, r.path, r.http_method, r.description, " +
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
}
