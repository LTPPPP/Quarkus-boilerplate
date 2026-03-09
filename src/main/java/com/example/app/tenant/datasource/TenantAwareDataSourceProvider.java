package com.example.app.tenant.datasource;

import com.example.app.tenant.context.TenantContext;
import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.domain.TenantRepository;
import com.example.app.util.EncryptionUtil;

import io.agroal.api.AgroalDataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TenantAwareDataSourceProvider {

    private static final Logger LOG = Logger.getLogger(TenantAwareDataSourceProvider.class);

    private final Map<String, Long> tenantSchemaLastAccess = new ConcurrentHashMap<>();

    @Inject
    AgroalDataSource defaultDataSource;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    EncryptionUtil encryptionUtil;

    @ConfigProperty(name = "app.multitenancy.default-schema", defaultValue = "public")
    String defaultSchema;

    @ConfigProperty(name = "app.multitenancy.strategy", defaultValue = "SCHEMA")
    String strategy;

    public Connection getConnection() throws SQLException {
        String tenantId = TenantContext.getCurrentTenant();
        Connection connection = defaultDataSource.getConnection();

        if ("SCHEMA".equalsIgnoreCase(strategy) && !TenantContext.DEFAULT_TENANT.equals(tenantId)) {
            setSchemaForConnection(connection, tenantId);
            tenantSchemaLastAccess.put(tenantId, System.currentTimeMillis());
        }

        return connection;
    }

    public void setSchemaForConnection(Connection connection, String tenantId) throws SQLException {
        String schemaName = resolveSchemaName(tenantId);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + schemaName + ", public");
            LOG.debugf("Set search_path to %s for tenant %s", schemaName, tenantId);
        }
    }

    public String resolveSchemaName(String tenantId) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
        if (tenant != null && tenant.getSchemaName() != null && !tenant.getSchemaName().isBlank()) {
            return tenant.getSchemaName();
        }
        return "tenant_" + tenantId;
    }

    public void evictTenant(String tenantId) {
        tenantSchemaLastAccess.remove(tenantId);
        LOG.infof("Evicted tenant datasource cache entry for: %s", tenantId);
    }

    public void createSchema(String tenantId) throws SQLException {
        String schemaName = "tenant_" + tenantId;
        try (Connection conn = defaultDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            LOG.infof("Created schema: %s for tenant: %s", schemaName, tenantId);
        }
    }

    public void dropSchema(String tenantId) throws SQLException {
        String schemaName = "tenant_" + tenantId;
        try (Connection conn = defaultDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            LOG.infof("Dropped schema: %s for tenant: %s", schemaName, tenantId);
        }
    }
}
