package com.example.app.tenant.domain;

import com.example.app.tenant.context.TenantContext;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class TenantAwareRepository<T extends TenantAwareEntity>
        implements PanacheRepositoryBase<T, UUID> {

    public List<T> findAllForCurrentTenant() {
        return list("tenantId", TenantContext.getCurrentTenant());
    }

    public Optional<T> findByIdForCurrentTenant(UUID id) {
        return find("id = ?1 and tenantId = ?2", id, TenantContext.getCurrentTenant())
                .firstResultOptional();
    }

    public long countForCurrentTenant() {
        return count("tenantId", TenantContext.getCurrentTenant());
    }

    public List<T> listByTenant(String tenantId, Page page) {
        return find("tenantId", tenantId).page(page).list();
    }

    public void deleteByIdForCurrentTenant(UUID id) {
        delete("id = ?1 and tenantId = ?2", id, TenantContext.getCurrentTenant());
    }

    public boolean existsForCurrentTenant(UUID id) {
        return count("id = ?1 and tenantId = ?2", id, TenantContext.getCurrentTenant()) > 0;
    }
}
