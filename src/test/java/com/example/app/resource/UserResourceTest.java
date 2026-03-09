package com.example.app.resource;

import com.example.app.domain.UserEntity;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.context.TenantContext;
import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.domain.TenantRepository;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class UserResourceTest {

    private static final String TEST_TENANT = "resource-test-tenant";

    @Inject
    TenantRepository tenantRepository;

    @Inject
    UserRepository userRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        if (!tenantRepository.existsByTenantId(TEST_TENANT)) {
            TenantEntity tenant = new TenantEntity();
            tenant.setTenantId(TEST_TENANT);
            tenant.setName("Resource Test");
            tenant.setStatus(TenantEntity.TenantStatus.ACTIVE);
            tenant.setPlan(TenantEntity.TenantPlan.STARTER);
            tenant.setMaxUsers(100);
            tenant.setStorageQuotaGb(10);
            tenantRepository.persist(tenant);
        }
    }

    @Test
    void testCreateUserViaRest() {
        String uniqueEmail = "rest-create-" + System.currentTimeMillis() + "@test.com";

        given()
                .header("X-Tenant-ID", TEST_TENANT)
                .contentType("application/json")
                .body("""
                        {
                            "email": "%s",
                            "password": "TestPass123!",
                            "fullName": "REST Test User",
                            "role": "USER"
                        }
                        """.formatted(uniqueEmail))
                .when()
                .post("/api/v1/users")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("email", is(uniqueEmail))
                .body("fullName", is("REST Test User"));
    }

    @Test
    void testCreateUserMissingTenantReturns401() {
        given()
                .contentType("application/json")
                .body("""
                        {
                            "email": "no-tenant@test.com",
                            "password": "TestPass123!",
                            "fullName": "No Tenant",
                            "role": "USER"
                        }
                        """)
                .when()
                .post("/api/v1/users")
                .then()
                .statusCode(401);
    }

    @Test
    @Transactional
    void testGetUserById() {
        String email = "rest-get-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFullName("Get Me");
        user.setRole("USER");
        user.setTenantId(TEST_TENANT);
        user.setStatus(UserEntity.UserStatus.ACTIVE);
        userRepository.persist(user);

        given()
                .header("X-Tenant-ID", TEST_TENANT)
                .when()
                .get("/api/v1/users/" + user.getId())
                .then()
                .statusCode(200)
                .body("email", is(email))
                .body("fullName", is("Get Me"));
    }

    @Test
    void testGetUserNotFound() {
        given()
                .header("X-Tenant-ID", TEST_TENANT)
                .when()
                .get("/api/v1/users/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void testListUsers() {
        given()
                .header("X-Tenant-ID", TEST_TENANT)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/users")
                .then()
                .statusCode(200);
    }
}
