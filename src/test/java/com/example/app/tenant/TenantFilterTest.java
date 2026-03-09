package com.example.app.tenant;

import com.example.app.tenant.context.TenantContext;
import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.domain.TenantRepository;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TenantFilterTest {

    @Inject
    TenantRepository tenantRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        if (!tenantRepository.existsByTenantId("test-tenant")) {
            TenantEntity tenant = new TenantEntity();
            tenant.setTenantId("test-tenant");
            tenant.setName("Test Tenant");
            tenant.setStatus(TenantEntity.TenantStatus.ACTIVE);
            tenant.setPlan(TenantEntity.TenantPlan.STARTER);
            tenant.setMaxUsers(100);
            tenant.setStorageQuotaGb(10);
            tenantRepository.persist(tenant);
        }
    }

    @Test
    void testPublicPathDoesNotRequireTenant() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200);
    }

    @Test
    void testTenantContextSetAndCleared() {
        TenantContext.setCurrentTenant("unit-test-tenant");
        assertEquals("unit-test-tenant", TenantContext.getCurrentTenant());

        TenantContext.clear();
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.getCurrentTenant());
    }

    @Test
    void testTenantContextValidation() {
        TenantContext.setCurrentTenant("valid-tenant");
        assertTrue(TenantContext.isValid());

        TenantContext.setCurrentTenant("INVALID TENANT!");
        assertFalse(TenantContext.isValid());

        TenantContext.clear();
    }

    @Test
    void testTenantContextIsSet() {
        assertFalse(TenantContext.isSet());

        TenantContext.setCurrentTenant("some-tenant");
        assertTrue(TenantContext.isSet());

        TenantContext.clear();
        assertFalse(TenantContext.isSet());
    }
}
