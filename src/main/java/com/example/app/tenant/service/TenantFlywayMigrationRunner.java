package com.example.app.tenant.service;

import io.agroal.api.AgroalDataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TenantFlywayMigrationRunner {

    private static final Logger LOG = Logger.getLogger(TenantFlywayMigrationRunner.class);

    @Inject
    AgroalDataSource dataSource;

    public void runMigrations(String tenantId) {
        String schemaName = "tenant_" + tenantId;
        LOG.infof("Running Flyway migrations for tenant schema: %s", schemaName);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/tenant-migrations")
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();

        LOG.infof("Flyway migrations completed successfully for schema: %s", schemaName);
    }

    public void repairMigrations(String tenantId) {
        String schemaName = "tenant_" + tenantId;
        LOG.infof("Repairing Flyway migrations for tenant schema: %s", schemaName);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/tenant-migrations")
                .load();

        flyway.repair();

        LOG.infof("Flyway repair completed for schema: %s", schemaName);
    }
}
