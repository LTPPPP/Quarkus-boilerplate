package com.example.app.repository;

import com.example.app.domain.UserEntity;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<UserEntity, UUID> {

    public Optional<UserEntity> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public List<UserEntity> findByTenantId(String tenantId) {
        return list("tenantId", tenantId);
    }

    public long countByTenantId(String tenantId) {
        return count("tenantId", tenantId);
    }

    public boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }

    public List<UserEntity> findByTenantIdAndRole(String tenantId, String role) {
        return list("tenantId = ?1 and role = ?2", tenantId, role);
    }
}
