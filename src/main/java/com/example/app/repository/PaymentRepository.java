package com.example.app.repository;

import com.example.app.domain.PaymentEntity;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PaymentRepository implements PanacheRepositoryBase<PaymentEntity, UUID> {

    public Optional<PaymentEntity> findByOrderId(UUID orderId) {
        return find("orderId", orderId).firstResultOptional();
    }

    public List<PaymentEntity> findByTenantId(String tenantId) {
        return list("tenantId", tenantId);
    }

    public Optional<PaymentEntity> findByGatewayRef(String gatewayRef) {
        return find("gatewayRef", gatewayRef).firstResultOptional();
    }
}
