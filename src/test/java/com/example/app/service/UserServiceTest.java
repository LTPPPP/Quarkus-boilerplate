package com.example.app.service;

import com.example.app.domain.UserEntity;
import com.example.app.exception.ConflictException;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.context.TenantContext;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class UserServiceTest {

    private static final String TEST_TENANT = "svc-test-tenant";

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TEST_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void testCreateUser() {
        String email = "create-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = userService.createUser(email, "hashed-password", "Test User", "USER");

        assertNotNull(user, "Created user should not be null");
        assertNotNull(user.getId(), "User ID should be generated");
        assertEquals(email, user.getEmail());
        assertEquals("Test User", user.getFullName());
        assertEquals("USER", user.getRole());
        assertEquals(TEST_TENANT, user.getTenantId());
        assertEquals(UserEntity.UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    @Transactional
    void testCreateUserWithNullEmail() {
        assertThrows(ValidationException.class,
                () -> userService.createUser(null, "hash", "Name", "USER"));
    }

    @Test
    @Transactional
    void testCreateUserWithBlankEmail() {
        assertThrows(ValidationException.class,
                () -> userService.createUser("  ", "hash", "Name", "USER"));
    }

    @Test
    @Transactional
    void testCreateDuplicateEmailThrowsConflict() {
        String email = "dup-" + System.currentTimeMillis() + "@test.com";
        userService.createUser(email, "hash1", "First", "USER");

        assertThrows(ConflictException.class,
                () -> userService.createUser(email, "hash2", "Second", "USER"));
    }

    @Test
    @Transactional
    void testFindById() {
        String email = "find-" + System.currentTimeMillis() + "@test.com";
        UserEntity created = userService.createUser(email, "hash", "Find Me", "USER");

        UserEntity found = userService.findById(created.getId());
        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals(email, found.getEmail());
    }

    @Test
    void testFindByIdNotFound() {
        UUID nonExistent = UUID.randomUUID();
        assertThrows(NotFoundException.class,
                () -> userService.findById(nonExistent));
    }

    @Test
    @Transactional
    void testExistsByEmail() {
        String email = "exists-" + System.currentTimeMillis() + "@test.com";
        assertFalse(userService.existsByEmail(email));

        userService.createUser(email, "hash", "Exists", "USER");
        assertTrue(userService.existsByEmail(email));
    }

    @Test
    @Transactional
    void testUpdateUser() {
        String email = "update-" + System.currentTimeMillis() + "@test.com";
        UserEntity created = userService.createUser(email, "hash", "Original", "USER");

        UserEntity updated = userService.updateUser(created.getId(), "Updated Name", "ADMIN", true);
        assertEquals("Updated Name", updated.getFullName());
        assertEquals("ADMIN", updated.getRole());
        assertEquals(UserEntity.UserStatus.ACTIVE, updated.getStatus());
    }

    @Test
    @Transactional
    void testUpdateUserDeactivate() {
        String email = "deactivate-" + System.currentTimeMillis() + "@test.com";
        UserEntity created = userService.createUser(email, "hash", "Deactivate", "USER");

        UserEntity updated = userService.updateUser(created.getId(), null, null, false);
        assertEquals(UserEntity.UserStatus.INACTIVE, updated.getStatus());
        assertEquals("Deactivate", updated.getFullName());
    }

    @Test
    @Transactional
    void testDeleteUser() {
        String email = "delete-" + System.currentTimeMillis() + "@test.com";
        UserEntity created = userService.createUser(email, "hash", "Delete Me", "USER");
        UUID id = created.getId();

        userService.deleteUser(id);

        assertThrows(NotFoundException.class, () -> userService.findById(id));
    }

    @Test
    @Transactional
    void testListByTenantIdWithPagination() {
        String tenant = "list-tenant-" + System.currentTimeMillis();
        TenantContext.setCurrentTenant(tenant);

        for (int i = 0; i < 5; i++) {
            UserEntity user = new UserEntity();
            user.setEmail("list-" + i + "-" + System.currentTimeMillis() + "@test.com");
            user.setPasswordHash("hash");
            user.setFullName("User " + i);
            user.setRole("USER");
            user.setTenantId(tenant);
            user.setStatus(UserEntity.UserStatus.ACTIVE);
            userRepository.persist(user);
        }

        List<UserEntity> page1 = userService.listByTenantId(tenant, 0, 3);
        assertEquals(3, page1.size(), "First page should have 3 users");

        List<UserEntity> page2 = userService.listByTenantId(tenant, 1, 3);
        assertEquals(2, page2.size(), "Second page should have 2 users");

        long total = userService.countByTenantId(tenant);
        assertEquals(5, total, "Total count should be 5");
    }

    @Test
    @Transactional
    void testCreateUserDefaultRole() {
        String email = "default-role-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = userService.createUser(email, "hash", "Default Role", null);
        assertEquals("USER", user.getRole(), "Default role should be USER");
    }
}
