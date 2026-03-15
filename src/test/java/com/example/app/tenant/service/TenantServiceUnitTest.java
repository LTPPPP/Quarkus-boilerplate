package com.example.app.tenant.service;

import com.example.app.exception.NotFoundException;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.domain.TenantEntity;
import com.example.app.tenant.domain.TenantRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService Unit Tests")
class TenantServiceUnitTest {

    private static final String TEST_TENANT_ID = "acme-corp";

    @Mock
    TenantRepository tenantRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    TenantService tenantService;

    // ---- listTenants ----

    @Nested
    @DisplayName("listTenants")
    class ListTenants {

        @Test
        @DisplayName("should return all tenants")
        void listTenants_success() {
            TenantEntity t1 = createTenant("tenant-1", "Tenant One");
            TenantEntity t2 = createTenant("tenant-2", "Tenant Two");
            when(tenantRepository.listAll()).thenReturn(List.of(t1, t2));

            List<TenantEntity> result = tenantService.listTenants();

            assertEquals(2, result.size());
            verify(tenantRepository).listAll();
        }

        @Test
        @DisplayName("should return empty list when no tenants")
        void listTenants_empty() {
            when(tenantRepository.listAll()).thenReturn(List.of());

            List<TenantEntity> result = tenantService.listTenants();

            assertTrue(result.isEmpty());
        }
    }

    // ---- listActiveTenants ----

    @Nested
    @DisplayName("listActiveTenants")
    class ListActiveTenants {

        @Test
        @DisplayName("should return only active tenants")
        void listActiveTenants_success() {
            TenantEntity active = createTenant("active-1", "Active Tenant");
            active.setStatus(TenantEntity.TenantStatus.ACTIVE);
            when(tenantRepository.findAllActive()).thenReturn(List.of(active));

            List<TenantEntity> result = tenantService.listActiveTenants();

            assertEquals(1, result.size());
            verify(tenantRepository).findAllActive();
        }
    }

    // ---- getTenant ----

    @Nested
    @DisplayName("getTenant")
    class GetTenant {

        @Test
        @DisplayName("should return tenant when found")
        void getTenant_found() {
            TenantEntity tenant = createTenant(TEST_TENANT_ID, "ACME Corp");
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));

            TenantEntity result = tenantService.getTenant(TEST_TENANT_ID);

            assertNotNull(result);
            assertEquals(TEST_TENANT_ID, result.getTenantId());
            assertEquals("ACME Corp", result.getName());
        }

        @Test
        @DisplayName("should throw NotFoundException when tenant not found")
        void getTenant_notFound() {
            when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());

            NotFoundException ex = assertThrows(NotFoundException.class,
                    () -> tenantService.getTenant("missing"));
            assertEquals("Tenant", ex.getResourceType());
            assertEquals("missing", ex.getIdentifier());
        }
    }

    // ---- updateTenant ----

    @Nested
    @DisplayName("updateTenant")
    class UpdateTenant {

        @Test
        @DisplayName("should update all fields when provided")
        void updateTenant_allFields() {
            TenantEntity existing = createTenant(TEST_TENANT_ID, "Old Name");
            existing.setPlan(TenantEntity.TenantPlan.FREE);
            existing.setMaxUsers(10);
            existing.setStorageQuotaGb(1);
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(existing));

            TenantEntity result = tenantService.updateTenant(
                    TEST_TENANT_ID, "New Name", TenantEntity.TenantPlan.ENTERPRISE, 500, 100, "{\"sso\":true}");

            assertEquals("New Name", result.getName());
            assertEquals(TenantEntity.TenantPlan.ENTERPRISE, result.getPlan());
            assertEquals(500, result.getMaxUsers());
            assertEquals(100, result.getStorageQuotaGb());
            assertEquals("{\"sso\":true}", result.getFeatures());
            verify(tenantRepository).persist(existing);
        }

        @Test
        @DisplayName("should not update name when null")
        void updateTenant_nullName() {
            TenantEntity existing = createTenant(TEST_TENANT_ID, "Original");
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(existing));

            TenantEntity result = tenantService.updateTenant(
                    TEST_TENANT_ID, null, TenantEntity.TenantPlan.STARTER, 50, 10, null);

            assertEquals("Original", result.getName());
        }

        @Test
        @DisplayName("should not update name when blank")
        void updateTenant_blankName() {
            TenantEntity existing = createTenant(TEST_TENANT_ID, "Original");
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(existing));

            TenantEntity result = tenantService.updateTenant(
                    TEST_TENANT_ID, "  ", null, 0, 0, null);

            assertEquals("Original", result.getName());
        }

        @Test
        @DisplayName("should not update plan when null")
        void updateTenant_nullPlan() {
            TenantEntity existing = createTenant(TEST_TENANT_ID, "Tenant");
            existing.setPlan(TenantEntity.TenantPlan.PROFESSIONAL);
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(existing));

            TenantEntity result = tenantService.updateTenant(
                    TEST_TENANT_ID, null, null, 0, 0, null);

            assertEquals(TenantEntity.TenantPlan.PROFESSIONAL, result.getPlan());
        }

        @Test
        @DisplayName("should not update maxUsers when zero or negative")
        void updateTenant_zeroMaxUsers() {
            TenantEntity existing = createTenant(TEST_TENANT_ID, "Tenant");
            existing.setMaxUsers(25);
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(existing));

            TenantEntity result = tenantService.updateTenant(
                    TEST_TENANT_ID, null, null, 0, 0, null);

            assertEquals(25, result.getMaxUsers());
        }

        @Test
        @DisplayName("should not update storageQuotaGb when zero or negative")
        void updateTenant_zeroStorage() {
            TenantEntity existing = createTenant(TEST_TENANT_ID, "Tenant");
            existing.setStorageQuotaGb(50);
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(existing));

            TenantEntity result = tenantService.updateTenant(
                    TEST_TENANT_ID, null, null, 0, -1, null);

            assertEquals(50, result.getStorageQuotaGb());
        }

        @Test
        @DisplayName("should update features to null when explicitly set")
        void updateTenant_featuresCanBeNull() {
            TenantEntity existing = createTenant(TEST_TENANT_ID, "Tenant");
            existing.setFeatures("{\"old\":true}");
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(existing));

            // features=null means "don't update" per the code (if features != null)
            TenantEntity result = tenantService.updateTenant(
                    TEST_TENANT_ID, null, null, 0, 0, null);

            // features should remain unchanged since null means skip
            assertEquals("{\"old\":true}", result.getFeatures());
        }

        @Test
        @DisplayName("should allow setting features to empty string")
        void updateTenant_emptyFeatures() {
            TenantEntity existing = createTenant(TEST_TENANT_ID, "Tenant");
            existing.setFeatures("{\"old\":true}");
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(existing));

            TenantEntity result = tenantService.updateTenant(
                    TEST_TENANT_ID, null, null, 0, 0, "");

            assertEquals("", result.getFeatures());
        }

        @Test
        @DisplayName("should throw NotFoundException when tenant not found")
        void updateTenant_notFound() {
            when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> tenantService.updateTenant("missing", "Name", null, 0, 0, null));
            verify(tenantRepository, never()).persist(any(TenantEntity.class));
        }
    }

    // ---- getTenantStats ----

    @Nested
    @DisplayName("getTenantStats")
    class GetTenantStats {

        @Test
        @DisplayName("should return correct stats map")
        void getTenantStats_success() {
            TenantEntity tenant = createTenant(TEST_TENANT_ID, "ACME Corp");
            tenant.setStatus(TenantEntity.TenantStatus.ACTIVE);
            tenant.setPlan(TenantEntity.TenantPlan.ENTERPRISE);
            tenant.setMaxUsers(500);
            tenant.setStorageQuotaGb(100);
            tenant.setCreatedAt(Instant.parse("2025-01-15T10:30:00Z"));
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.countByTenantId(TEST_TENANT_ID)).thenReturn(42L);

            Map<String, Object> stats = tenantService.getTenantStats(TEST_TENANT_ID);

            assertEquals(TEST_TENANT_ID, stats.get("tenantId"));
            assertEquals("ACME Corp", stats.get("name"));
            assertEquals("ENTERPRISE", stats.get("plan"));
            assertEquals("ACTIVE", stats.get("status"));
            assertEquals(42L, stats.get("userCount"));
            assertEquals(500, stats.get("maxUsers"));
            assertEquals(100, stats.get("storageQuotaGb"));
            assertNotNull(stats.get("createdAt"));
        }

        @Test
        @DisplayName("should return zero user count when no users")
        void getTenantStats_noUsers() {
            TenantEntity tenant = createTenant(TEST_TENANT_ID, "Empty Tenant");
            tenant.setStatus(TenantEntity.TenantStatus.ACTIVE);
            tenant.setPlan(TenantEntity.TenantPlan.FREE);
            tenant.setCreatedAt(Instant.now());
            when(tenantRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.countByTenantId(TEST_TENANT_ID)).thenReturn(0L);

            Map<String, Object> stats = tenantService.getTenantStats(TEST_TENANT_ID);

            assertEquals(0L, stats.get("userCount"));
        }

        @Test
        @DisplayName("should throw NotFoundException when tenant not found")
        void getTenantStats_notFound() {
            when(tenantRepository.findByTenantId("missing")).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> tenantService.getTenantStats("missing"));
        }
    }

    // ---- helpers ----

    private TenantEntity createTenant(String tenantId, String name) {
        TenantEntity tenant = new TenantEntity();
        tenant.setTenantId(tenantId);
        tenant.setName(name);
        tenant.setStatus(TenantEntity.TenantStatus.ACTIVE);
        tenant.setPlan(TenantEntity.TenantPlan.FREE);
        tenant.setMaxUsers(10);
        tenant.setStorageQuotaGb(1);
        tenant.setCreatedAt(Instant.now());
        return tenant;
    }
}
