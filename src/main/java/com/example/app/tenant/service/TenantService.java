package com.example.app.tenant.service;

import com.example.app.exception.NotFoundException;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.domain.TenantRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TenantService {

    private static final Logger LOG = Logger.getLogger(TenantService.class);

    @Inject
    TenantRepository tenantRepository;

    @Inject
    UserRepository userRepository;

    public List<TenantEntity> listTenants() {
        return tenantRepository.listAll();
    }

    public List<TenantEntity> listActiveTenants() {
        return tenantRepository.findAllActive();
    }

    public TenantEntity getTenant(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId));
    }

    @Transactional
    public TenantEntity updateTenant(String tenantId, String name, TenantEntity.TenantPlan plan,
                                      int maxUsers, int storageQuotaGb, String features) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId));

        if (name != null && !name.isBlank()) {
            tenant.setName(name);
        }
        if (plan != null) {
            tenant.setPlan(plan);
        }
        if (maxUsers > 0) {
            tenant.setMaxUsers(maxUsers);
        }
        if (storageQuotaGb > 0) {
            tenant.setStorageQuotaGb(storageQuotaGb);
        }
        if (features != null) {
            tenant.setFeatures(features);
        }

        tenantRepository.persist(tenant);
        LOG.infof("Tenant updated: %s", tenantId);
        return tenant;
    }

    public Map<String, Object> getTenantStats(String tenantId) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId));

        long userCount = userRepository.countByTenantId(tenantId);

        return Map.of(
                "tenantId", tenantId,
                "name", tenant.getName(),
                "plan", tenant.getPlan().name(),
                "status", tenant.getStatus().name(),
                "userCount", userCount,
                "maxUsers", tenant.getMaxUsers(),
                "storageQuotaGb", tenant.getStorageQuotaGb(),
                "createdAt", tenant.getCreatedAt().toString()
        );
    }
}
