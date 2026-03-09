package com.example.app.tenant.service;

import com.example.app.event.domain.EventType;
import com.example.app.event.domain.UserEvent;
import com.example.app.event.domain.UserEventPayload;
import com.example.app.event.producer.KafkaEventProducer;
import com.example.app.exception.ConflictException;
import com.example.app.exception.ValidationException;
import com.example.app.tenant.datasource.TenantAwareDataSourceProvider;
import com.example.app.tenant.domain.TenantConfig;
import com.example.app.tenant.domain.TenantConfigRepository;
import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.domain.TenantRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.util.regex.Pattern;

@ApplicationScoped
public class TenantProvisioningService {

    private static final Logger LOG = Logger.getLogger(TenantProvisioningService.class);
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{2,29}$");

    @Inject
    TenantRepository tenantRepository;

    @Inject
    TenantConfigRepository tenantConfigRepository;

    @Inject
    TenantAwareDataSourceProvider dataSourceProvider;

    @Inject
    TenantFlywayMigrationRunner migrationRunner;

    @Inject
    KafkaEventProducer kafkaEventProducer;

    @Transactional
    public TenantEntity provisionTenant(String tenantId, String name, TenantEntity.TenantPlan plan,
                                         int maxUsers, int storageQuotaGb) {
        validateTenantId(tenantId);

        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new ConflictException("Tenant", tenantId);
        }

        TenantEntity tenant = new TenantEntity();
        tenant.setTenantId(tenantId);
        tenant.setName(name);
        tenant.setPlan(plan != null ? plan : TenantEntity.TenantPlan.FREE);
        tenant.setMaxUsers(maxUsers > 0 ? maxUsers : getDefaultMaxUsers(tenant.getPlan()));
        tenant.setStorageQuotaGb(storageQuotaGb > 0 ? storageQuotaGb : getDefaultStorageGb(tenant.getPlan()));
        tenant.setStatus(TenantEntity.TenantStatus.PROVISIONING);
        tenant.setSchemaName("tenant_" + tenantId);

        tenantRepository.persist(tenant);
        LOG.infof("Tenant entity created: %s (status=PROVISIONING)", tenantId);

        try {
            dataSourceProvider.createSchema(tenantId);
            LOG.infof("Schema created for tenant: %s", tenantId);

            migrationRunner.runMigrations(tenantId);
            LOG.infof("Migrations completed for tenant: %s", tenantId);

            seedDefaultData(tenant);
            LOG.infof("Default data seeded for tenant: %s", tenantId);

            tenant.setStatus(TenantEntity.TenantStatus.ACTIVE);
            tenantRepository.persist(tenant);
            LOG.infof("Tenant provisioned successfully: %s (status=ACTIVE)", tenantId);

            publishTenantProvisionedEvent(tenant);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to provision tenant: %s", tenantId);
            tenant.setStatus(TenantEntity.TenantStatus.SUSPENDED);
            tenantRepository.persist(tenant);
            throw new RuntimeException("Tenant provisioning failed: " + e.getMessage(), e);
        }

        return tenant;
    }

    @Transactional
    public TenantEntity suspendTenant(String tenantId) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new com.example.app.exception.NotFoundException("Tenant", tenantId));

        tenant.setStatus(TenantEntity.TenantStatus.SUSPENDED);
        tenantRepository.persist(tenant);
        dataSourceProvider.evictTenant(tenantId);

        LOG.infof("Tenant suspended: %s", tenantId);
        return tenant;
    }

    @Transactional
    public TenantEntity activateTenant(String tenantId) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new com.example.app.exception.NotFoundException("Tenant", tenantId));

        tenant.setStatus(TenantEntity.TenantStatus.ACTIVE);
        tenantRepository.persist(tenant);

        LOG.infof("Tenant activated: %s", tenantId);
        return tenant;
    }

    @Transactional
    public void deleteTenant(String tenantId) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new com.example.app.exception.NotFoundException("Tenant", tenantId));

        tenant.setStatus(TenantEntity.TenantStatus.DELETED);
        tenantRepository.persist(tenant);
        dataSourceProvider.evictTenant(tenantId);

        LOG.infof("Tenant soft-deleted: %s (hard delete scheduled after 30 days)", tenantId);
    }

    private void validateTenantId(String tenantId) {
        if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new ValidationException(
                    "Invalid tenantId: must be 3-30 chars, lowercase alphanumeric with hyphens/underscores, " +
                            "starting with alphanumeric. Got: " + tenantId);
        }
    }

    private void seedDefaultData(TenantEntity tenant) {
        TenantConfig themeConfig = new TenantConfig();
        themeConfig.setTenantId(tenant.getTenantId());
        themeConfig.setConfigKey("ui.theme");
        themeConfig.setConfigValue("light");
        themeConfig.setEncrypted(false);
        tenantConfigRepository.persist(themeConfig);

        TenantConfig langConfig = new TenantConfig();
        langConfig.setTenantId(tenant.getTenantId());
        langConfig.setConfigKey("ui.language");
        langConfig.setConfigValue("en");
        langConfig.setEncrypted(false);
        tenantConfigRepository.persist(langConfig);

        TenantConfig notifConfig = new TenantConfig();
        notifConfig.setTenantId(tenant.getTenantId());
        notifConfig.setConfigKey("notifications.enabled");
        notifConfig.setConfigValue("true");
        notifConfig.setEncrypted(false);
        tenantConfigRepository.persist(notifConfig);

        LOG.infof("Seeded default configuration for tenant: %s", tenant.getTenantId());
    }

    private void publishTenantProvisionedEvent(TenantEntity tenant) {
        UserEventPayload payload = new UserEventPayload(
                tenant.getId().toString(),
                null,
                tenant.getName(),
                "TENANT",
                "PROVISIONED"
        );
        UserEvent event = UserEvent.of(EventType.TENANT_PROVISIONED, payload, tenant.getTenantId());
        kafkaEventProducer.publish("notification-events-out", event);
        LOG.infof("Published TENANT_PROVISIONED event for tenant: %s", tenant.getTenantId());
    }

    private int getDefaultMaxUsers(TenantEntity.TenantPlan plan) {
        return switch (plan) {
            case FREE -> 5;
            case STARTER -> 25;
            case PROFESSIONAL -> 100;
            case ENTERPRISE -> 1000;
        };
    }

    private int getDefaultStorageGb(TenantEntity.TenantPlan plan) {
        return switch (plan) {
            case FREE -> 1;
            case STARTER -> 10;
            case PROFESSIONAL -> 100;
            case ENTERPRISE -> 1000;
        };
    }
}
