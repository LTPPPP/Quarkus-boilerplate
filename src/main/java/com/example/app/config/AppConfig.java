package com.example.app.config;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class AppConfig {

    @ConfigProperty(name = "app.multitenancy.strategy", defaultValue = "SCHEMA")
    String multitenancyStrategy;

    @ConfigProperty(name = "app.multitenancy.tenant-header", defaultValue = "X-Tenant-ID")
    String tenantHeader;

    @ConfigProperty(name = "app.multitenancy.default-schema", defaultValue = "public")
    String defaultSchema;

    @ConfigProperty(name = "app.multitenancy.cache.datasource-ttl", defaultValue = "PT30M")
    Duration datasourceTtl;

    @ConfigProperty(name = "app.multitenancy.public-paths",
            defaultValue = "/api/v1/auth,/api/v1/public,/q/health,/q/metrics,/q/openapi")
    List<String> publicPaths;

    @ConfigProperty(name = "app.cors.allowed-origins", defaultValue = "*")
    String corsAllowedOrigins;

    @ConfigProperty(name = "app.encryption.key", defaultValue = "change-this-default-key-32chars!")
    String encryptionKey;

    public String getMultitenancyStrategy() {
        return multitenancyStrategy;
    }

    public String getTenantHeader() {
        return tenantHeader;
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public Duration getDatasourceTtl() {
        return datasourceTtl;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public boolean isSchemaStrategy() {
        return "SCHEMA".equalsIgnoreCase(multitenancyStrategy);
    }

    public boolean isRowLevelStrategy() {
        return "ROW_LEVEL".equalsIgnoreCase(multitenancyStrategy);
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }
}
