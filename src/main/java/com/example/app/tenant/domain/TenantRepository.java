package com.example.app.tenant.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TenantRepository implements PanacheRepositoryBase<TenantEntity, UUID> {

    public Optional<TenantEntity> findByTenantId(String tenantId) {
        return find("tenantId", tenantId).firstResultOptional();
    }

    public boolean existsByTenantId(String tenantId) {
        return count("tenantId", tenantId) > 0;
    }

    public List<TenantEntity> findByStatus(TenantEntity.TenantStatus status) {
        return list("status", status);
    }

    public List<TenantEntity> findAllActive() {
        return list("status", TenantEntity.TenantStatus.ACTIVE);
    }

    public List<TenantEntity> findByPlan(TenantEntity.TenantPlan plan) {
        return list("plan", plan);
    }
}
