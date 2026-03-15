package com.example.app.service;

import com.example.app.domain.UserEntity;
import com.example.app.event.producer.UserEventProducer;
import com.example.app.exception.ConflictException;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.context.TenantContext;

import io.quarkus.panache.common.Page;
import io.quarkus.hibernate.orm.panache.PanacheQuery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceUnitTest {

    private static final String TEST_TENANT = "test-tenant";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_HASH = "hashed-password";
    private static final String TEST_NAME = "Test User";

    @Mock
    UserRepository userRepository;

    @Mock
    UserEventProducer userEventProducer;

    @InjectMocks
    UserService userService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TEST_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- findById ----

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return user when found")
        void findById_found() {
            UUID id = UUID.randomUUID();
            UserEntity user = createUser(id, TEST_EMAIL);
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.of(user));

            UserEntity result = userService.findById(id);

            assertNotNull(result);
            assertEquals(id, result.getId());
            verify(userRepository).findByIdOptional(id);
        }

        @Test
        @DisplayName("should throw NotFoundException when not found")
        void findById_notFound() {
            UUID id = UUID.randomUUID();
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> userService.findById(id));
        }
    }

    // ---- findByEmail ----

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("should return user when email exists")
        void findByEmail_found() {
            UserEntity user = createUser(UUID.randomUUID(), TEST_EMAIL);
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

            UserEntity result = userService.findByEmail(TEST_EMAIL);

            assertNotNull(result);
            assertEquals(TEST_EMAIL, result.getEmail());
        }

        @Test
        @DisplayName("should throw NotFoundException when email not found")
        void findByEmail_notFound() {
            when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> userService.findByEmail("missing@test.com"));
        }
    }

    // ---- existsByEmail ----

    @Nested
    @DisplayName("existsByEmail")
    class ExistsByEmail {

        @Test
        @DisplayName("should return true when email exists")
        void existsByEmail_true() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);
            assertTrue(userService.existsByEmail(TEST_EMAIL));
        }

        @Test
        @DisplayName("should return false when email does not exist")
        void existsByEmail_false() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            assertFalse(userService.existsByEmail(TEST_EMAIL));
        }
    }

    // ---- createUser ----

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create user with valid input")
        void createUser_success() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);

            UserEntity result = userService.createUser(TEST_EMAIL, TEST_HASH, TEST_NAME, "USER");

            assertNotNull(result);
            assertEquals(TEST_EMAIL, result.getEmail());
            assertEquals(TEST_HASH, result.getPasswordHash());
            assertEquals(TEST_NAME, result.getFullName());
            assertEquals("USER", result.getRole());
            assertEquals(TEST_TENANT, result.getTenantId());
            assertEquals(UserEntity.UserStatus.ACTIVE, result.getStatus());

            verify(userRepository).persist(any(UserEntity.class));
            verify(userEventProducer).publishUserCreated(any(UserEntity.class), eq(TEST_TENANT));
        }

        @Test
        @DisplayName("should default role to USER when null")
        void createUser_defaultRole() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);

            UserEntity result = userService.createUser(TEST_EMAIL, TEST_HASH, TEST_NAME, null);

            assertEquals("USER", result.getRole());
        }

        @Test
        @DisplayName("should throw ValidationException when email is null")
        void createUser_nullEmail() {
            assertThrows(ValidationException.class,
                    () -> userService.createUser(null, TEST_HASH, TEST_NAME, "USER"));
            verify(userRepository, never()).persist(any(UserEntity.class));
        }

        @Test
        @DisplayName("should throw ValidationException when email is blank")
        void createUser_blankEmail() {
            assertThrows(ValidationException.class,
                    () -> userService.createUser("  ", TEST_HASH, TEST_NAME, "USER"));
            verify(userRepository, never()).persist(any(UserEntity.class));
        }

        @Test
        @DisplayName("should throw ConflictException when email already exists")
        void createUser_duplicateEmail() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

            assertThrows(ConflictException.class,
                    () -> userService.createUser(TEST_EMAIL, TEST_HASH, TEST_NAME, "USER"));
            verify(userRepository, never()).persist(any(UserEntity.class));
        }

        @Test
        @DisplayName("should still succeed even if event publishing fails")
        void createUser_eventPublishingFails() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            doThrow(new RuntimeException("Kafka down"))
                    .when(userEventProducer).publishUserCreated(any(), anyString());

            UserEntity result = userService.createUser(TEST_EMAIL, TEST_HASH, TEST_NAME, "USER");

            assertNotNull(result);
            verify(userRepository).persist(any(UserEntity.class));
        }

        @Test
        @DisplayName("should use current tenant from TenantContext")
        void createUser_usesTenantContext() {
            String customTenant = "custom-tenant";
            TenantContext.setCurrentTenant(customTenant);
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);

            UserEntity result = userService.createUser(TEST_EMAIL, TEST_HASH, TEST_NAME, "USER");

            assertEquals(customTenant, result.getTenantId());
        }
    }

    // ---- updateUser ----

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("should update all fields")
        void updateUser_allFields() {
            UUID id = UUID.randomUUID();
            UserEntity existing = createUser(id, TEST_EMAIL);
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.of(existing));

            UserEntity result = userService.updateUser(id, "New Name", "ADMIN", true);

            assertEquals("New Name", result.getFullName());
            assertEquals("ADMIN", result.getRole());
            assertEquals(UserEntity.UserStatus.ACTIVE, result.getStatus());
            verify(userRepository).persist(existing);
            verify(userEventProducer).publishUserUpdated(any(UserEntity.class), eq(TEST_TENANT));
        }

        @Test
        @DisplayName("should not update fullName when null")
        void updateUser_nullFullName() {
            UUID id = UUID.randomUUID();
            UserEntity existing = createUser(id, TEST_EMAIL);
            existing.setFullName("Original");
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.of(existing));

            UserEntity result = userService.updateUser(id, null, "ADMIN", true);

            assertEquals("Original", result.getFullName());
        }

        @Test
        @DisplayName("should not update fullName when blank")
        void updateUser_blankFullName() {
            UUID id = UUID.randomUUID();
            UserEntity existing = createUser(id, TEST_EMAIL);
            existing.setFullName("Original");
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.of(existing));

            UserEntity result = userService.updateUser(id, "  ", null, true);

            assertEquals("Original", result.getFullName());
        }

        @Test
        @DisplayName("should deactivate user")
        void updateUser_deactivate() {
            UUID id = UUID.randomUUID();
            UserEntity existing = createUser(id, TEST_EMAIL);
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.of(existing));

            UserEntity result = userService.updateUser(id, null, null, false);

            assertEquals(UserEntity.UserStatus.INACTIVE, result.getStatus());
        }

        @Test
        @DisplayName("should throw NotFoundException when user does not exist")
        void updateUser_notFound() {
            UUID id = UUID.randomUUID();
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> userService.updateUser(id, "Name", "USER", true));
        }

        @Test
        @DisplayName("should still succeed even if event publishing fails")
        void updateUser_eventPublishingFails() {
            UUID id = UUID.randomUUID();
            UserEntity existing = createUser(id, TEST_EMAIL);
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.of(existing));
            doThrow(new RuntimeException("Kafka down"))
                    .when(userEventProducer).publishUserUpdated(any(), anyString());

            UserEntity result = userService.updateUser(id, "Updated", "USER", true);

            assertNotNull(result);
            verify(userRepository).persist(existing);
        }
    }

    // ---- deleteUser ----

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("should delete existing user")
        void deleteUser_success() {
            UUID id = UUID.randomUUID();
            UserEntity existing = createUser(id, TEST_EMAIL);
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.of(existing));

            userService.deleteUser(id);

            verify(userRepository).delete(existing);
            verify(userEventProducer).publishUserDeleted(id, TEST_TENANT);
        }

        @Test
        @DisplayName("should throw NotFoundException when user does not exist")
        void deleteUser_notFound() {
            UUID id = UUID.randomUUID();
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> userService.deleteUser(id));
            verify(userRepository, never()).delete(any(UserEntity.class));
        }

        @Test
        @DisplayName("should still succeed even if event publishing fails")
        void deleteUser_eventPublishingFails() {
            UUID id = UUID.randomUUID();
            UserEntity existing = createUser(id, TEST_EMAIL);
            when(userRepository.findByIdOptional(id)).thenReturn(Optional.of(existing));
            doThrow(new RuntimeException("Kafka down"))
                    .when(userEventProducer).publishUserDeleted(any(), anyString());

            userService.deleteUser(id);

            verify(userRepository).delete(existing);
        }
    }

    // ---- listByTenantId ----

    @Nested
    @DisplayName("listByTenantId")
    class ListByTenantId {

        @Test
        @DisplayName("should enforce max page size of 100")
        @SuppressWarnings("unchecked")
        void listByTenantId_maxPageSize() {
            PanacheQuery<UserEntity> mockQuery = mock(PanacheQuery.class);
            PanacheQuery<UserEntity> pagedQuery = mock(PanacheQuery.class);
            when(userRepository.find("tenantId", TEST_TENANT)).thenReturn(mockQuery);
            when(mockQuery.page(any(Page.class))).thenReturn(pagedQuery);
            when(pagedQuery.list()).thenReturn(List.of());

            userService.listByTenantId(TEST_TENANT, 0, 500);

            ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
            verify(mockQuery).page(pageCaptor.capture());
            assertEquals(100, pageCaptor.getValue().size);
        }

        @Test
        @DisplayName("should default page size to 20 when size is 0 or negative")
        @SuppressWarnings("unchecked")
        void listByTenantId_defaultPageSize() {
            PanacheQuery<UserEntity> mockQuery = mock(PanacheQuery.class);
            PanacheQuery<UserEntity> pagedQuery = mock(PanacheQuery.class);
            when(userRepository.find("tenantId", TEST_TENANT)).thenReturn(mockQuery);
            when(mockQuery.page(any(Page.class))).thenReturn(pagedQuery);
            when(pagedQuery.list()).thenReturn(List.of());

            userService.listByTenantId(TEST_TENANT, 0, 0);

            ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
            verify(mockQuery).page(pageCaptor.capture());
            assertEquals(20, pageCaptor.getValue().size);
        }

        @Test
        @DisplayName("should default page size to 20 when size is negative")
        @SuppressWarnings("unchecked")
        void listByTenantId_negativePageSize() {
            PanacheQuery<UserEntity> mockQuery = mock(PanacheQuery.class);
            PanacheQuery<UserEntity> pagedQuery = mock(PanacheQuery.class);
            when(userRepository.find("tenantId", TEST_TENANT)).thenReturn(mockQuery);
            when(mockQuery.page(any(Page.class))).thenReturn(pagedQuery);
            when(pagedQuery.list()).thenReturn(List.of());

            userService.listByTenantId(TEST_TENANT, 0, -5);

            ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
            verify(mockQuery).page(pageCaptor.capture());
            assertEquals(20, pageCaptor.getValue().size);
        }
    }

    // ---- countByTenantId ----

    @Test
    @DisplayName("countByTenantId should delegate to repository")
    void countByTenantId() {
        when(userRepository.countByTenantId(TEST_TENANT)).thenReturn(42L);

        long count = userService.countByTenantId(TEST_TENANT);

        assertEquals(42L, count);
        verify(userRepository).countByTenantId(TEST_TENANT);
    }

    // ---- helpers ----

    private UserEntity createUser(UUID id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(TEST_HASH);
        user.setFullName(TEST_NAME);
        user.setRole("USER");
        user.setTenantId(TEST_TENANT);
        user.setStatus(UserEntity.UserStatus.ACTIVE);
        return user;
    }
}
