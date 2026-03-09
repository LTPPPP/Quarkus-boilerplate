package com.example.app.tenant.service;

import com.example.app.exception.QuotaExceededException;
import com.example.app.repository.OrderRepository;
import com.example.app.repository.PaymentRepository;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.domain.TenantRepository;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.jboss.logging.Logger;

import java.time.Duration;

@ApplicationScoped
public class QuotaEnforcementService {

    private static final Logger LOG = Logger.getLogger(QuotaEnforcementService.class);
    private static final String STORAGE_CACHE_PREFIX = "quota:storage:";
    private static final Duration STORAGE_CACHE_TTL = Duration.ofMinutes(5);

    @Inject
    TenantRepository tenantRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    OrderRepository orderRepository;

    @Inject
    EntityManager entityManager;

    private final ValueCommands<String, String> redisValues;

    public QuotaEnforcementService(RedisDataSource redisDataSource) {
        this.redisValues = redisDataSource.value(String.class);
    }

    public void checkUserQuota(String tenantId) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        long currentUserCount = userRepository.countByTenantId(tenantId);
        if (currentUserCount >= tenant.getMaxUsers()) {
            LOG.warnf("User quota exceeded for tenant %s: %d/%d",
                    tenantId, currentUserCount, tenant.getMaxUsers());
            throw new QuotaExceededException(tenantId,
                    String.format("Maximum users limit reached: %d/%d", currentUserCount, tenant.getMaxUsers()));
        }

        LOG.debugf("User quota check passed for tenant %s: %d/%d",
                tenantId, currentUserCount, tenant.getMaxUsers());
    }

    public void checkOrderQuota(String tenantId, int maxOrdersPerUser, String userId) {
        long orderCount = orderRepository.count("userId = ?1 and tenantId = ?2 and status != 'CANCELLED'",
                java.util.UUID.fromString(userId), tenantId);
        if (orderCount >= maxOrdersPerUser) {
            throw new QuotaExceededException(tenantId,
                    String.format("Active order limit reached for user %s: %d/%d", userId, orderCount, maxOrdersPerUser));
        }
    }

    public void checkStorageQuota(String tenantId, long bytesToAdd) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        long quotaBytes = (long) tenant.getStorageQuotaGb() * 1024 * 1024 * 1024;
        long currentUsage = getCurrentStorageUsage(tenantId);
        long projectedUsage = currentUsage + bytesToAdd;

        if (projectedUsage > quotaBytes) {
            LOG.warnf("Storage quota exceeded for tenant %s: projected %d bytes > quota %d bytes",
                    tenantId, projectedUsage, quotaBytes);
            throw new QuotaExceededException(tenantId,
                    String.format("Storage quota exceeded: %d MB used of %d GB",
                            currentUsage / (1024 * 1024), tenant.getStorageQuotaGb()));
        }
    }

    public QuotaSnapshot getQuotaSnapshot(String tenantId) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        long userCount = userRepository.countByTenantId(tenantId);
        long orderCount = orderRepository.countByTenantId(tenantId);
        long storageBytes = getCurrentStorageUsage(tenantId);
        long storageQuotaBytes = (long) tenant.getStorageQuotaGb() * 1024 * 1024 * 1024;

        return new QuotaSnapshot(
                tenantId,
                userCount, tenant.getMaxUsers(),
                orderCount,
                storageBytes, storageQuotaBytes
        );
    }

    private long getCurrentStorageUsage(String tenantId) {
        String cacheKey = STORAGE_CACHE_PREFIX + tenantId;
        String cached = redisValues.get(cacheKey);
        if (cached != null) {
            return Long.parseLong(cached);
        }

        long totalBytes = calculateStorageFromDatabase(tenantId);

        redisValues.setex(cacheKey, STORAGE_CACHE_TTL.getSeconds(), String.valueOf(totalBytes));
        LOG.debugf("Storage usage calculated for tenant %s: %d bytes", tenantId, totalBytes);
        return totalBytes;
    }

    private long calculateStorageFromDatabase(String tenantId) {
        long totalBytes = 0;

        try {
            Number userBytes = (Number) entityManager.createNativeQuery(
                    "SELECT COALESCE(SUM(pg_column_size(t.*)), 0) FROM users t WHERE t.tenant_id = :tid")
                    .setParameter("tid", tenantId)
                    .getSingleResult();
            totalBytes += userBytes.longValue();

            Number orderBytes = (Number) entityManager.createNativeQuery(
                    "SELECT COALESCE(SUM(pg_column_size(t.*)), 0) FROM orders t WHERE t.tenant_id = :tid")
                    .setParameter("tid", tenantId)
                    .getSingleResult();
            totalBytes += orderBytes.longValue();

            Number paymentBytes = (Number) entityManager.createNativeQuery(
                    "SELECT COALESCE(SUM(pg_column_size(t.*)), 0) FROM payments t WHERE t.tenant_id = :tid")
                    .setParameter("tid", tenantId)
                    .getSingleResult();
            totalBytes += paymentBytes.longValue();

            Number outboxBytes = (Number) entityManager.createNativeQuery(
                    "SELECT COALESCE(SUM(pg_column_size(t.*)), 0) FROM outbox_events t WHERE t.tenant_id = :tid")
                    .setParameter("tid", tenantId)
                    .getSingleResult();
            totalBytes += outboxBytes.longValue();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to calculate storage for tenant %s via pg_column_size, falling back to row count estimation", tenantId);
            totalBytes = estimateStorageByRowCount(tenantId);
        }

        return totalBytes;
    }

    private long estimateStorageByRowCount(String tenantId) {
        long userCount = userRepository.countByTenantId(tenantId);
        long orderCount = orderRepository.countByTenantId(tenantId);

        long estimatedAvgUserRowBytes = 512;
        long estimatedAvgOrderRowBytes = 1024;

        return (userCount * estimatedAvgUserRowBytes) + (orderCount * estimatedAvgOrderRowBytes);
    }

    public void invalidateStorageCache(String tenantId) {
        String cacheKey = STORAGE_CACHE_PREFIX + tenantId;
        redisValues.getdel(cacheKey);
    }

    public record QuotaSnapshot(
            String tenantId,
            long currentUsers, int maxUsers,
            long currentOrders,
            long currentStorageBytes, long maxStorageBytes
    ) {
        public double userUsagePercent() {
            return maxUsers > 0 ? ((double) currentUsers / maxUsers) * 100 : 0;
        }

        public double storageUsagePercent() {
            return maxStorageBytes > 0 ? ((double) currentStorageBytes / maxStorageBytes) * 100 : 0;
        }
    }
}
