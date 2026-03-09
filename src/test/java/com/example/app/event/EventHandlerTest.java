package com.example.app.event;

import com.example.app.event.domain.EventType;
import com.example.app.event.domain.OrderEventPayload;
import com.example.app.event.domain.PaymentEventPayload;
import com.example.app.event.domain.UserEventPayload;
import com.example.app.event.handler.PaymentEventHandler;
import com.example.app.event.handler.UserEventHandler;
import com.example.app.tenant.context.TenantContext;
import com.example.app.tenant.service.TenantAwareCache;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class EventHandlerTest {

    private static final String TEST_TENANT = "handler-test-tenant";

    @Inject
    UserEventHandler userEventHandler;

    @Inject
    PaymentEventHandler paymentEventHandler;

    @Inject
    TenantAwareCache tenantAwareCache;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TEST_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testUserCreatedHandlerCachesPreferences() {
        String userId = UUID.randomUUID().toString();
        UserEventPayload payload = new UserEventPayload(userId, "handler@test.com", "Handler User", "USER", "CREATED");

        assertDoesNotThrow(() -> userEventHandler.onUserCreated(payload, TEST_TENANT));

        String prefs = tenantAwareCache.get("user:prefs:" + userId);
        assertNotNull(prefs, "User preferences should be cached after USER_CREATED");
    }

    @Test
    void testUserCreatedHandlerCachesProfile() {
        String userId = UUID.randomUUID().toString();
        UserEventPayload payload = new UserEventPayload(userId, "profile@test.com", "Profile User", "ADMIN", "CREATED");

        userEventHandler.onUserCreated(payload, TEST_TENANT);

        String profile = tenantAwareCache.get("user:" + userId);
        assertNotNull(profile, "User profile should be cached");
    }

    @Test
    void testUserCreatedHandlerQueuesNotification() {
        String userId = UUID.randomUUID().toString();
        UserEventPayload payload = new UserEventPayload(userId, "notif@test.com", "Notif User", "USER", "CREATED");

        userEventHandler.onUserCreated(payload, TEST_TENANT);

        String notif = tenantAwareCache.get("notification:pending:" + userId + ":welcome");
        assertNotNull(notif, "Welcome notification should be queued");
    }

    @Test
    void testUserUpdatedHandlerInvalidatesAndRecaches() {
        String userId = UUID.randomUUID().toString();
        UserEventPayload createPayload = new UserEventPayload(userId, "update@test.com", "Before Update", "USER", "CREATED");
        userEventHandler.onUserCreated(createPayload, TEST_TENANT);

        UserEventPayload updatePayload = new UserEventPayload(userId, "update@test.com", "After Update", "ADMIN", "UPDATED");
        userEventHandler.onUserUpdated(updatePayload, TEST_TENANT);

        String profile = tenantAwareCache.get("user:" + userId);
        assertNotNull(profile, "Profile cache should be refreshed after update");
    }

    @Test
    void testUserDeletedHandlerCleansUpResources() {
        String userId = UUID.randomUUID().toString();
        UserEventPayload createPayload = new UserEventPayload(userId, "delete@test.com", "Delete Me", "USER", "CREATED");
        userEventHandler.onUserCreated(createPayload, TEST_TENANT);

        UserEventPayload deletePayload = new UserEventPayload(userId, "delete@test.com", "Delete Me", "USER", "DELETED");
        userEventHandler.onUserDeleted(deletePayload, TEST_TENANT);

        assertNull(tenantAwareCache.get("user:" + userId), "User profile cache should be cleared");
        assertNull(tenantAwareCache.get("user:prefs:" + userId), "User preferences should be cleared");
    }

    @Test
    void testPaymentInitiatedCachesStatus() {
        String paymentId = UUID.randomUUID().toString();
        PaymentEventPayload payload = new PaymentEventPayload(
                paymentId, UUID.randomUUID().toString(), new BigDecimal("100.00"), "USD", "INITIATED", "GW-TEST");

        assertDoesNotThrow(() -> paymentEventHandler.onPaymentInitiated(payload, TEST_TENANT));

        String status = tenantAwareCache.get("payment:status:" + paymentId);
        assertNotNull(status, "Payment status should be cached");
    }

    @Test
    void testPaymentSuccessCachesStatusAndNotification() {
        String paymentId = UUID.randomUUID().toString();
        PaymentEventPayload payload = new PaymentEventPayload(
                paymentId, UUID.randomUUID().toString(), new BigDecimal("50.00"), "USD", "SUCCESS", "GW-OK");

        assertDoesNotThrow(() -> paymentEventHandler.onPaymentSuccess(payload, TEST_TENANT));

        String status = tenantAwareCache.get("payment:status:" + paymentId);
        assertNotNull(status, "Payment status should be cached as SUCCESS");

        String notif = tenantAwareCache.get("notification:payment:success:" + paymentId);
        assertNotNull(notif, "Payment confirmation notification should be queued");
    }

    @Test
    void testPaymentFailedCachesStatusAndNotification() {
        String paymentId = UUID.randomUUID().toString();
        PaymentEventPayload payload = new PaymentEventPayload(
                paymentId, UUID.randomUUID().toString(), new BigDecimal("75.00"), "EUR", "FAILED", "GW-FAIL");

        assertDoesNotThrow(() -> paymentEventHandler.onPaymentFailed(payload, TEST_TENANT));

        String status = tenantAwareCache.get("payment:status:" + paymentId);
        assertNotNull(status, "Payment status should be cached as FAILED");

        String notif = tenantAwareCache.get("notification:payment:failed:" + paymentId);
        assertNotNull(notif, "Payment failure notification should be queued");
    }
}
