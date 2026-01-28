package com.zula.apihealth.repository;

import com.zula.apihealth.config.ApiHealthProperties;
import com.zula.apihealth.model.ApiCallLogEntry;
import com.zula.apihealth.model.ApiEndpointView;
import com.zula.apihealth.model.ApiLogView;
import com.zula.database.core.DatabaseManager;
import org.jdbi.v3.core.Jdbi;

import java.time.OffsetDateTime;
import java.util.List;

public class ApiHealthRepository {
    private final Jdbi jdbi;
    private final ApiHealthProperties properties;
    private final String schema;

    public ApiHealthRepository(Jdbi jdbi, DatabaseManager databaseManager, ApiHealthProperties properties) {
        this.jdbi = jdbi;
        this.properties = properties;
        String raw = properties.getSchemaName() != null && !properties.getSchemaName().isBlank()
                ? properties.getSchemaName()
                : databaseManager.generateSchemaName();
        this.schema = com.zula.apihealth.config.ApiHealthSchemaInitializer.sanitize(raw);
    }

    public void registerEndpointIfAbsent(String name, String path, String method, String description) {
        jdbi.useHandle(handle -> handle.createUpdate("INSERT INTO " + schema + ".api_endpoint_registry (name, path, http_method, description) " +
                        "VALUES (:name, :path, :method, :description) " +
                        "ON CONFLICT (path, http_method) DO NOTHING")
                .bind("name", name)
                .bind("path", path)
                .bind("method", method)
                .bind("description", description)
                .execute());
    }

    public List<ApiEndpointView> listEndpointsWithStats() {
        String sql = """
            SELECT r.id, r.name, r.path, r.http_method, r.description,
                   COALESCE(count(l.id),0) AS total_calls,
                   COALESCE(sum(CASE WHEN l.success THEN 1 ELSE 0 END),0) AS success_calls,
                   COALESCE(sum(CASE WHEN NOT l.success THEN 1 ELSE 0 END),0) AS failure_calls,
                   COALESCE(avg(l.duration_ms),0) AS avg_duration_ms,
                   max(l."timestamp") AS last_called
            FROM %s.api_endpoint_registry r
            LEFT JOIN %s.api_call_logs l ON l.url LIKE r.path || '%%'
            GROUP BY r.id, r.name, r.path, r.http_method, r.description
            ORDER BY r.id
            """.formatted(schema, schema);
        return jdbi.withHandle(h -> h.createQuery(sql)
                .map((rs, ctx) -> {
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
                    v.setLastCalled(rs.getObject("last_called", OffsetDateTime.class));
                    return v;
                })
                .list());
    }

    public ApiEndpointView getEndpointWithStats(long id) {
        String sql = """
            SELECT r.id, r.name, r.path, r.http_method, r.description,
                   COALESCE(count(l.id),0) AS total_calls,
                   COALESCE(sum(CASE WHEN l.success THEN 1 ELSE 0 END),0) AS success_calls,
                   COALESCE(sum(CASE WHEN NOT l.success THEN 1 ELSE 0 END),0) AS failure_calls,
                   COALESCE(avg(l.duration_ms),0) AS avg_duration_ms,
                   max(l."timestamp") AS last_called
            FROM %s.api_endpoint_registry r
            LEFT JOIN %s.api_call_logs l ON l.url LIKE r.path || '%%'
            WHERE r.id = :id
            GROUP BY r.id, r.name, r.path, r.http_method, r.description
            """.formatted(schema, schema);
        return jdbi.withHandle(h -> h.createQuery(sql)
                .bind("id", id)
                .map((rs, ctx) -> {
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
                    v.setLastCalled(rs.getObject("last_called", OffsetDateTime.class));
                    return v;
                })
                .findOne()
                .orElse(null));
    }

    public List<ApiLogView> recentLogs(int limit) {
        String sql = "SELECT id, \"timestamp\", url, http_method, http_status, duration_ms, success, trace_id " +
                "FROM " + schema + ".api_call_logs ORDER BY \"timestamp\" DESC LIMIT :limit";
        return jdbi.withHandle(h -> h.createQuery(sql)
                .bind("limit", limit)
                .map((rs, ctx) -> mapLog(rs))
                .list());
    }

    public List<ApiLogView> logsByEndpoint(String endpointLike, int limit) {
        String sql = "SELECT id, \"timestamp\", url, http_method, http_status, duration_ms, success, trace_id " +
                "FROM " + schema + ".api_call_logs " +
                "WHERE url LIKE :endpointLike ORDER BY \"timestamp\" DESC LIMIT :limit";
        return jdbi.withHandle(h -> h.createQuery(sql)
                .bind("endpointLike", endpointLike + "%")
                .bind("limit", limit)
                .map((rs, ctx) -> mapLog(rs))
                .list());
    }

    public List<ApiLogView> logsForEndpointId(long endpointId, int limit) {
        String sql = """
            SELECT l.id, l."timestamp", l.url, l.http_method, l.http_status, l.duration_ms, l.success, l.trace_id
            FROM %s.api_call_logs l
            JOIN %s.api_endpoint_registry r ON l.url LIKE r.path || '%%'
            WHERE r.id = :id
            ORDER BY l."timestamp" DESC
            LIMIT :limit
            """.formatted(schema, schema);
        return jdbi.withHandle(h -> h.createQuery(sql)
                .bind("id", endpointId)
                .bind("limit", limit)
                .map((rs, ctx) -> mapLog(rs))
                .list());
    }

    public void insertLog(ApiCallLogEntry entry) {
        String sql = "INSERT INTO " + schema + ".api_call_logs " +
                "(id, \"timestamp\", url, http_method, request_headers, request_body, response_headers, response_body, http_status, duration_ms, trace_id, success, error_message) " +
                "VALUES (:id, :timestamp, :url, :method, :reqHeaders, :reqBody, :resHeaders, :resBody, :status, :duration, :traceId, :success, :error)";
        jdbi.useHandle(h -> h.createUpdate(sql)
                .bind("id", entry.getId())
                .bind("timestamp", entry.getTimestamp())
                .bind("url", entry.getUrl())
                .bind("method", entry.getHttpMethod())
                .bind("reqHeaders", entry.getRequestHeaders())
                .bind("reqBody", entry.getRequestBody())
                .bind("resHeaders", entry.getResponseHeaders())
                .bind("resBody", entry.getResponseBody())
                .bind("status", entry.getHttpStatus())
                .bind("duration", entry.getDurationMs())
                .bind("traceId", entry.getTraceId())
                .bind("success", entry.getSuccess())
                .bind("error", entry.getErrorMessage())
                .execute());
    }

    private ApiLogView mapLog(java.sql.ResultSet rs) throws java.sql.SQLException {
        ApiLogView v = new ApiLogView();
        v.setId((java.util.UUID) rs.getObject("id"));
        v.setTimestamp(rs.getObject("timestamp", OffsetDateTime.class));
        v.setUrl(rs.getString("url"));
        v.setHttpMethod(rs.getString("http_method"));
        v.setHttpStatus(rs.getObject("http_status") == null ? null : rs.getInt("http_status"));
        v.setDurationMs(rs.getObject("duration_ms") == null ? null : rs.getInt("duration_ms"));
        v.setSuccess(rs.getObject("success") == null ? null : rs.getBoolean("success"));
        v.setTraceId(rs.getString("trace_id"));
        return v;
    }
}
