package com.example.app.config;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis-based distributed lock service using SET NX EX pattern.
 * Provides safe acquire/release via Lua scripts to prevent accidental unlock
 * of locks owned by other instances.
 *
 * Usage:
 * <pre>
 *   String result = distributedLockService.executeWithLock(
 *       "payment:" + orderId, Duration.ofSeconds(30),
 *       () -> processPayment(orderId)
 *   );
 * </pre>
 */
@ApplicationScoped
public class DistributedLockService {

    private static final Logger LOG = Logger.getLogger(DistributedLockService.class);

    /**
     * Lua script for safe unlock: only deletes the key if the stored value matches
     * the caller's lock token, preventing accidental release of another holder's lock.
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end";

    private final ValueCommands<String, String> valueCommands;
    private final RedisDataSource redisDataSource;
    private final Duration defaultTtl;
    private final long retryIntervalMs;
    private final int maxRetries;

    public DistributedLockService(
            RedisDataSource redisDataSource,
            @ConfigProperty(name = "app.distributed-lock.default-ttl-seconds", defaultValue = "30") int defaultTtlSeconds,
            @ConfigProperty(name = "app.distributed-lock.retry-interval-ms", defaultValue = "100") long retryIntervalMs,
            @ConfigProperty(name = "app.distributed-lock.max-retries", defaultValue = "50") int maxRetries) {
        this.redisDataSource = redisDataSource;
        this.valueCommands = redisDataSource.value(String.class);
        this.defaultTtl = Duration.ofSeconds(defaultTtlSeconds);
        this.retryIntervalMs = retryIntervalMs;
        this.maxRetries = maxRetries;
    }

    /**
     * Attempts to acquire a distributed lock.
     *
     * @param key the lock key
     * @param ttl how long the lock should be held before auto-expiry
     * @return the lock token if acquired, null if the lock is already held
     */
    public String tryLock(String key, Duration ttl) {
        String lockKey = "lock:" + key;
        String token = UUID.randomUUID().toString();
        Duration effectiveTtl = ttl != null ? ttl : defaultTtl;

        SetArgs args = new SetArgs().nx().ex(effectiveTtl.getSeconds());
        valueCommands.set(lockKey, token, args);

        // Verify the lock was actually acquired by reading back
        String stored = valueCommands.get(lockKey);
        if (token.equals(stored)) {
            LOG.debugf("Lock acquired: key=%s, token=%s, ttl=%s", lockKey, token, effectiveTtl);
            return token;
        }
        LOG.debugf("Lock not acquired (already held): key=%s", lockKey);
        return null;
    }

    /**
     * Attempts to acquire a lock with retries and blocking wait.
     *
     * @param key     the lock key
     * @param ttl     how long the lock should be held
     * @param timeout max time to wait for lock acquisition
     * @return the lock token if acquired, null if timed out
     */
    public String tryLockWithRetry(String key, Duration ttl, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int attempts = 0;

        while (System.currentTimeMillis() < deadline && attempts < maxRetries) {
            String token = tryLock(key, ttl);
            if (token != null) {
                return token;
            }
            attempts++;
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warnf("Lock acquisition interrupted: key=%s", key);
                return null;
            }
        }
        LOG.warnf("Lock acquisition timed out: key=%s, attempts=%d", key, attempts);
        return null;
    }

    /**
     * Releases a distributed lock. Uses Lua script to ensure only the lock holder
     * can release it (compare-and-delete).
     *
     * @param key   the lock key
     * @param token the token returned from tryLock
     * @return true if the lock was released, false if it was already expired or held by another
     */
    public boolean unlock(String key, String token) {
        String lockKey = "lock:" + key;
        try {
            Object result = redisDataSource.execute("eval", UNLOCK_SCRIPT, "1", lockKey, token);
            boolean released = result != null && !"0".equals(result.toString());
            if (released) {
                LOG.debugf("Lock released: key=%s, token=%s", lockKey, token);
            } else {
                LOG.warnf("Lock release failed (token mismatch or expired): key=%s", lockKey);
            }
            return released;
        } catch (Exception e) {
            LOG.errorf(e, "Error releasing lock: key=%s", lockKey);
            return false;
        }
    }

    /**
     * Executes a supplier while holding a distributed lock.
     * Acquires the lock, runs the action, and guarantees unlock in finally block.
     *
     * @param key    the lock key
     * @param ttl    how long the lock should be held
     * @param action the action to execute while holding the lock
     * @param <T>    return type
     * @return the result of the action
     * @throws DistributedLockException if the lock cannot be acquired
     */
    public <T> T executeWithLock(String key, Duration ttl, Supplier<T> action) {
        String token = tryLockWithRetry(key, ttl, Duration.ofSeconds(ttl.getSeconds() * 2));
        if (token == null) {
            throw new DistributedLockException("Failed to acquire lock: " + key);
        }
        try {
            return action.get();
        } finally {
            unlock(key, token);
        }
    }

    /**
     * Executes a runnable while holding a distributed lock (void variant).
     */
    public void executeWithLock(String key, Duration ttl, Runnable action) {
        executeWithLock(key, ttl, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Exception thrown when a distributed lock cannot be acquired.
     */
    public static class DistributedLockException extends RuntimeException {
        public DistributedLockException(String message) {
            super(message);
        }
    }
}
