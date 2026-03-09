package com.example.app.tenant.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TenantConfigRepository implements PanacheRepositoryBase<TenantConfig, UUID> {

    public List<TenantConfig> findByTenantId(String tenantId) {
        return list("tenantId", tenantId);
    }

    public Optional<TenantConfig> findByTenantIdAndKey(String tenantId, String configKey) {
        return find("tenantId = ?1 and configKey = ?2", tenantId, configKey).firstResultOptional();
    }

    public void deleteByTenantId(String tenantId) {
        delete("tenantId", tenantId);
    }
}
