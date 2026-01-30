package com.zula.apihealth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the API health module.
 * Prefix: zula.apihealth
 */
@ConfigurationProperties(prefix = "zula.apihealth")
public class ApiHealthProperties {
    /**
     * Schema / database name to use for tables. Defaults to 'api_health' if unset.
     */
    private String schemaName;

    /**
     * Create schema/tables automatically on startup.
     */
    private boolean autoCreateTables = true;

    /**
     * Default page size for recent logs.
     */
    private int recentLimit = 50;

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public boolean isAutoCreateTables() {
        return autoCreateTables;
    }

    public void setAutoCreateTables(boolean autoCreateTables) {
        this.autoCreateTables = autoCreateTables;
    }

    public int getRecentLimit() {
        return recentLimit;
    }

    public void setRecentLimit(int recentLimit) {
        this.recentLimit = recentLimit;
    }
}
