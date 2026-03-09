package com.example.app.tenant.service;

import com.example.app.tenant.context.TenantContext;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class TenantAwareCache {

    private static final Logger LOG = Logger.getLogger(TenantAwareCache.class);

    private final ValueCommands<String, String> valueCommands;
    private final KeyCommands<String> keyCommands;

    public TenantAwareCache(RedisDataSource redisDataSource) {
        this.valueCommands = redisDataSource.value(String.class);
        this.keyCommands = redisDataSource.key();
    }

    public String get(String key) {
        String fullKey = buildKey(key);
        String value = valueCommands.get(fullKey);
        LOG.debugf("Cache GET [%s] = %s", fullKey, value != null ? "hit" : "miss");
        return value;
    }

    public void put(String key, String value, Duration ttl) {
        String fullKey = buildKey(key);
        valueCommands.setex(fullKey, ttl.getSeconds(), value);
        LOG.debugf("Cache PUT [%s], ttl=%s", fullKey, ttl);
    }

    public void evict(String key) {
        String fullKey = buildKey(key);
        keyCommands.del(fullKey);
        LOG.debugf("Cache EVICT [%s]", fullKey);
    }

    public void evictAll() {
        String tenantId = TenantContext.getCurrentTenant();
        String pattern = tenantId + ":*";
        List<String> keys = keyCommands.keys(pattern);
        if (!keys.isEmpty()) {
            keyCommands.del(keys.toArray(new String[0]));
            LOG.infof("Cache EVICT ALL for tenant [%s], keys evicted: %d", tenantId, keys.size());
        }
    }

    private String buildKey(String key) {
        String tenantId = TenantContext.getCurrentTenant();
        return tenantId + ":" + key;
    }
}
