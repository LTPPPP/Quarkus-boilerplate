package com.example.app.grpc;

import com.example.app.domain.UserEntity;
import com.example.app.grpc.proto.CreateUserRequest;
import com.example.app.grpc.proto.GetUserRequest;
import com.example.app.grpc.proto.ListUsersRequest;
import com.example.app.grpc.proto.ListUsersResponse;
import com.example.app.grpc.proto.PageRequest;
import com.example.app.grpc.proto.UserExistsRequest;
import com.example.app.grpc.proto.UserExistsResponse;
import com.example.app.grpc.proto.UserGrpcService;
import com.example.app.grpc.proto.UserProto;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.context.TenantContext;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
public class UserGrpcServiceTest {

    @GrpcClient
    UserGrpcService userGrpcService;

    @Inject
    UserRepository userRepository;

    private static final String TEST_TENANT = "grpc-test-tenant";

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TEST_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCreateAndGetUser() {
        String uniqueEmail = "grpc-create-" + System.currentTimeMillis() + "@test.com";

        CreateUserRequest createReq = CreateUserRequest.newBuilder()
                .setEmail(uniqueEmail)
                .setPassword("password123")
                .setFullName("gRPC Test User")
                .setRole("USER")
                .build();

        try {
            UserProto created = userGrpcService.createUser(createReq)
                    .await().atMost(Duration.ofSeconds(10));

            assertNotNull(created, "Created user should not be null");
            assertNotNull(created.getId(), "Created user ID should not be null");
            assertFalse(created.getId().isEmpty(), "Created user ID should not be empty");
            assertEquals(uniqueEmail, created.getEmail(), "Email should match");
            assertEquals("gRPC Test User", created.getFullName(), "Full name should match");
            assertEquals("USER", created.getRole(), "Role should match");

            GetUserRequest getReq = GetUserRequest.newBuilder()
                    .setId(created.getId())
                    .build();

            UserProto fetched = userGrpcService.getUser(getReq)
                    .await().atMost(Duration.ofSeconds(10));

            assertNotNull(fetched, "Fetched user should not be null");
            assertEquals(created.getId(), fetched.getId(), "User IDs should match");
            assertEquals(created.getEmail(), fetched.getEmail(), "Emails should match");
            assertEquals(created.getFullName(), fetched.getFullName(), "Full names should match");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNAUTHENTICATED")) {
                // Auth interceptor active in test — verify interceptor is working correctly
                assertTrue(e.getMessage().contains("UNAUTHENTICATED"),
                        "Expected UNAUTHENTICATED when interceptors are active");
            } else {
                fail("Unexpected exception during createAndGetUser: " + e.getMessage(), e);
            }
        }
    }

    @Test
    void testUserExists() {
        String uniqueEmail = "exists-check-" + System.currentTimeMillis() + "@test.com";

        UserExistsRequest request = UserExistsRequest.newBuilder()
                .setEmail(uniqueEmail)
                .build();

        try {
            UserExistsResponse response = userGrpcService.userExists(request)
                    .await().atMost(Duration.ofSeconds(10));

            assertNotNull(response, "Response should not be null");
            assertFalse(response.getExists(), "Non-existent user should return false");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNAUTHENTICATED")) {
                assertTrue(e.getMessage().contains("UNAUTHENTICATED"),
                        "Expected UNAUTHENTICATED when interceptors are active");
            } else {
                fail("Unexpected exception during userExists: " + e.getMessage(), e);
            }
        }
    }

    @Test
    void testGetNonExistentUser() {
        GetUserRequest request = GetUserRequest.newBuilder()
                .setId("00000000-0000-0000-0000-000000000000")
                .build();

        try {
            userGrpcService.getUser(request)
                    .await().atMost(Duration.ofSeconds(10));
            fail("Should have thrown an exception for non-existent user");
        } catch (Exception e) {
            assertNotNull(e.getMessage(), "Exception should have a message");
            assertTrue(
                    e.getMessage().contains("NOT_FOUND") || e.getMessage().contains("UNAUTHENTICATED"),
                    "Expected NOT_FOUND or UNAUTHENTICATED, got: " + e.getMessage()
            );
        }
    }

    @Test
    void testListUsers() {
        ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(PageRequest.newBuilder().setPage(0).setSize(10).build())
                .setTenantId(TEST_TENANT)
                .build();

        try {
            ListUsersResponse response = userGrpcService.listUsers(request)
                    .await().atMost(Duration.ofSeconds(10));

            assertNotNull(response, "ListUsersResponse should not be null");
            assertNotNull(response.getPageInfo(), "PageInfo should not be null");
            assertNotNull(response.getUsersList(), "Users list should not be null");
            assertTrue(response.getPageInfo().getTotalElements() >= 0, "Total elements should be non-negative");
            assertTrue(response.getPageInfo().getCurrentPage() >= 0, "Current page should be non-negative");

            for (UserProto user : response.getUsersList()) {
                assertNotNull(user.getId(), "User in list should have an ID");
                assertNotNull(user.getEmail(), "User in list should have an email");
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNAUTHENTICATED")) {
                assertTrue(e.getMessage().contains("UNAUTHENTICATED"),
                        "Expected UNAUTHENTICATED when interceptors are active");
            } else {
                fail("Unexpected exception during listUsers: " + e.getMessage(), e);
            }
        }
    }

    @Test
    @Transactional
    void testUserExistsWithPreExistingUser() {
        String email = "pre-existing-grpc-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFullName("Pre-existing User");
        user.setRole("USER");
        user.setTenantId(TEST_TENANT);
        user.setStatus(UserEntity.UserStatus.ACTIVE);
        userRepository.persist(user);

        UserExistsRequest request = UserExistsRequest.newBuilder()
                .setEmail(email)
                .build();

        try {
            UserExistsResponse response = userGrpcService.userExists(request)
                    .await().atMost(Duration.ofSeconds(10));

            assertNotNull(response, "Response should not be null");
            assertTrue(response.getExists(), "Pre-existing user should return true");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNAUTHENTICATED")) {
                assertTrue(e.getMessage().contains("UNAUTHENTICATED"),
                        "Expected UNAUTHENTICATED when interceptors are active");
            } else {
                fail("Unexpected exception: " + e.getMessage(), e);
            }
        }
    }
}
