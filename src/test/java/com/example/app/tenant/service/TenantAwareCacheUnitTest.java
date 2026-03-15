package com.example.app.tenant.service;

import com.example.app.tenant.context.TenantContext;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantAwareCache Unit Tests")
class TenantAwareCacheUnitTest {

    private static final String TEST_TENANT = "test-tenant";

    @Mock
    RedisDataSource redisDataSource;

    @Mock
    ValueCommands<String, String> valueCommands;

    @Mock
    KeyCommands<String> keyCommands;

    private TenantAwareCache cache;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TEST_TENANT);
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(redisDataSource.key()).thenReturn(keyCommands);
        cache = new TenantAwareCache(redisDataSource);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- get ----

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("should return value when cache hit")
        void get_hit() {
            when(valueCommands.get(TEST_TENANT + ":my-key")).thenReturn("cached-value");

            String result = cache.get("my-key");

            assertEquals("cached-value", result);
            verify(valueCommands).get(TEST_TENANT + ":my-key");
        }

        @Test
        @DisplayName("should return null when cache miss")
        void get_miss() {
            when(valueCommands.get(TEST_TENANT + ":my-key")).thenReturn(null);

            String result = cache.get("my-key");

            assertNull(result);
        }

        @Test
        @DisplayName("should prefix key with tenant ID")
        void get_tenantPrefix() {
            String customTenant = "custom-tenant";
            TenantContext.setCurrentTenant(customTenant);
            when(valueCommands.get(customTenant + ":data")).thenReturn("val");

            String result = cache.get("data");

            assertEquals("val", result);
            verify(valueCommands).get(customTenant + ":data");
        }

        @Test
        @DisplayName("should use default tenant when none set")
        void get_defaultTenant() {
            TenantContext.clear();
            // TenantContext.getCurrentTenant() returns "public" by default
            when(valueCommands.get("public:key")).thenReturn("default-val");

            String result = cache.get("key");

            assertEquals("default-val", result);
            verify(valueCommands).get("public:key");
        }
    }

    // ---- put ----

    @Nested
    @DisplayName("put")
    class Put {

        @Test
        @DisplayName("should store value with TTL")
        void put_success() {
            Duration ttl = Duration.ofMinutes(5);

            cache.put("my-key", "my-value", ttl);

            verify(valueCommands).setex(TEST_TENANT + ":my-key", 300L, "my-value");
        }

        @Test
        @DisplayName("should handle short TTL")
        void put_shortTtl() {
            Duration ttl = Duration.ofSeconds(10);

            cache.put("key", "val", ttl);

            verify(valueCommands).setex(TEST_TENANT + ":key", 10L, "val");
        }

        @Test
        @DisplayName("should prefix key with tenant ID")
        void put_tenantPrefix() {
            String customTenant = "another-tenant";
            TenantContext.setCurrentTenant(customTenant);

            cache.put("data", "value", Duration.ofHours(1));

            verify(valueCommands).setex(customTenant + ":data", 3600L, "value");
        }
    }

    // ---- evict ----

    @Nested
    @DisplayName("evict")
    class Evict {

        @Test
        @DisplayName("should delete key with tenant prefix")
        void evict_success() {
            cache.evict("my-key");

            verify(keyCommands).del(TEST_TENANT + ":my-key");
        }

        @Test
        @DisplayName("should prefix key with correct tenant")
        void evict_tenantPrefix() {
            String customTenant = "other-tenant";
            TenantContext.setCurrentTenant(customTenant);

            cache.evict("session");

            verify(keyCommands).del(customTenant + ":session");
        }
    }

    // ---- evictAll ----

    @Nested
    @DisplayName("evictAll")
    class EvictAll {

        @Test
        @DisplayName("should delete all keys matching tenant pattern")
        void evictAll_withKeys() {
            List<String> matchingKeys = List.of(
                    TEST_TENANT + ":key1",
                    TEST_TENANT + ":key2",
                    TEST_TENANT + ":key3"
            );
            when(keyCommands.keys(TEST_TENANT + ":*")).thenReturn(matchingKeys);

            cache.evictAll();

            verify(keyCommands).keys(TEST_TENANT + ":*");
            verify(keyCommands).del(
                    TEST_TENANT + ":key1",
                    TEST_TENANT + ":key2",
                    TEST_TENANT + ":key3"
            );
        }

        @Test
        @DisplayName("should not call del when no keys match")
        void evictAll_noKeys() {
            when(keyCommands.keys(TEST_TENANT + ":*")).thenReturn(List.of());

            cache.evictAll();

            verify(keyCommands).keys(TEST_TENANT + ":*");
            verify(keyCommands, never()).del(any(String[].class));
        }

        @Test
        @DisplayName("should use correct tenant pattern")
        void evictAll_tenantPattern() {
            String customTenant = "my-org";
            TenantContext.setCurrentTenant(customTenant);
            when(keyCommands.keys(customTenant + ":*")).thenReturn(List.of(customTenant + ":x"));

            cache.evictAll();

            verify(keyCommands).keys(customTenant + ":*");
        }
    }
}
