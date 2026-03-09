package com.example.app.service;

import com.example.app.domain.UserEntity;
import com.example.app.event.producer.UserEventProducer;
import com.example.app.exception.ConflictException;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.context.TenantContext;

import io.quarkus.panache.common.Page;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    private static final Logger LOG = Logger.getLogger(UserService.class);

    @Inject
    UserRepository userRepository;

    @Inject
    UserEventProducer userEventProducer;

    public UserEntity findById(UUID id) {
        return userRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("User", id.toString()));
    }

    public UserEntity findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User", email));
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public List<UserEntity> listByTenantId(String tenantId, int page, int size) {
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        return userRepository.find("tenantId", tenantId)
                .page(Page.of(page, size))
                .list();
    }

    public long countByTenantId(String tenantId) {
        return userRepository.countByTenantId(tenantId);
    }

    @Transactional
    public UserEntity createUser(String email, String passwordHash, String fullName, String role) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email is required");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("User", "email");
        }

        String tenantId = TenantContext.getCurrentTenant();

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName);
        user.setRole(role != null ? role : "USER");
        user.setTenantId(tenantId);
        user.setStatus(UserEntity.UserStatus.ACTIVE);

        userRepository.persist(user);
        LOG.infof("User created: id=%s, email=%s, tenant=%s", user.getId(), email, tenantId);

        try {
            userEventProducer.publishUserCreated(user, tenantId);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish USER_CREATED event for user %s", user.getId());
        }

        return user;
    }

    @Transactional
    public UserEntity updateUser(UUID id, String fullName, String role, boolean isActive) {
        UserEntity user = findById(id);
        String tenantId = TenantContext.getCurrentTenant();

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }
        if (role != null && !role.isBlank()) {
            user.setRole(role);
        }
        user.setStatus(isActive ? UserEntity.UserStatus.ACTIVE : UserEntity.UserStatus.INACTIVE);

        userRepository.persist(user);
        LOG.infof("User updated: id=%s, tenant=%s", id, tenantId);

        try {
            userEventProducer.publishUserUpdated(user, tenantId);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish USER_UPDATED event for user %s", id);
        }

        return user;
    }

    @Transactional
    public void deleteUser(UUID id) {
        UserEntity user = findById(id);
        String tenantId = TenantContext.getCurrentTenant();

        userRepository.delete(user);
        LOG.infof("User deleted: id=%s, tenant=%s", id, tenantId);

        try {
            userEventProducer.publishUserDeleted(id, tenantId);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish USER_DELETED event for user %s", id);
        }
    }
}
