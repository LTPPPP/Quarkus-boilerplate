package com.example.app.tenant;

import com.example.app.domain.UserEntity;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.context.TenantContext;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class RowLevelSecurityTest {

    @Inject
    UserRepository userRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void testTenantDataIsolation() {
        UserEntity userA = new UserEntity();
        userA.setEmail("usera-" + System.currentTimeMillis() + "@tenant-a.com");
        userA.setPasswordHash("hash");
        userA.setFullName("User A");
        userA.setRole("USER");
        userA.setTenantId("tenant-a");
        userRepository.persist(userA);

        UserEntity userB = new UserEntity();
        userB.setEmail("userb-" + System.currentTimeMillis() + "@tenant-b.com");
        userB.setPasswordHash("hash");
        userB.setFullName("User B");
        userB.setRole("USER");
        userB.setTenantId("tenant-b");
        userRepository.persist(userB);

        List<UserEntity> tenantAUsers = userRepository.findByTenantId("tenant-a");
        List<UserEntity> tenantBUsers = userRepository.findByTenantId("tenant-b");

        assertTrue(tenantAUsers.stream().noneMatch(u -> u.getTenantId().equals("tenant-b")),
                "Tenant A should not see Tenant B data");
        assertTrue(tenantBUsers.stream().noneMatch(u -> u.getTenantId().equals("tenant-a")),
                "Tenant B should not see Tenant A data");
    }

    @Test
    @Transactional
    void testCountByTenant() {
        String tenantId = "count-tenant-" + System.currentTimeMillis();

        UserEntity user1 = new UserEntity();
        user1.setEmail("count1-" + System.currentTimeMillis() + "@test.com");
        user1.setPasswordHash("hash");
        user1.setFullName("Count User 1");
        user1.setRole("USER");
        user1.setTenantId(tenantId);
        userRepository.persist(user1);

        UserEntity user2 = new UserEntity();
        user2.setEmail("count2-" + System.currentTimeMillis() + "@test.com");
        user2.setPasswordHash("hash");
        user2.setFullName("Count User 2");
        user2.setRole("USER");
        user2.setTenantId(tenantId);
        userRepository.persist(user2);

        long count = userRepository.countByTenantId(tenantId);
        assertEquals(2, count);
    }
}
