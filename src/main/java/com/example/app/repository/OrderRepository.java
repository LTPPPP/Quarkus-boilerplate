package com.example.app.repository;

import com.example.app.domain.OrderEntity;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<OrderEntity, UUID> {

    public List<OrderEntity> findByUserId(UUID userId) {
        return list("userId", userId);
    }

    public List<OrderEntity> findByTenantId(String tenantId) {
        return list("tenantId", tenantId);
    }

    public List<OrderEntity> findByUserIdAndTenantId(UUID userId, String tenantId) {
        return list("userId = ?1 and tenantId = ?2", userId, tenantId);
    }

    public long countByTenantId(String tenantId) {
        return count("tenantId", tenantId);
    }
}
