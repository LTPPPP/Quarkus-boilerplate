package com.example.app.event;

import com.example.app.event.domain.EventType;
import com.example.app.event.domain.OrderEvent;
import com.example.app.event.domain.OrderEventPayload;
import com.example.app.event.domain.PaymentEvent;
import com.example.app.event.domain.PaymentEventPayload;
import com.example.app.event.domain.UserEvent;
import com.example.app.event.domain.UserEventPayload;
import com.example.app.util.JsonUtil;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;

import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class KafkaIntegrationTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @BeforeEach
    void setUp() {
        InMemorySink<Object> userEventsSink = connector.sink("user-events-out");
        userEventsSink.clear();
    }

    @Test
    void testUserEventSerializationRoundtrip() {
        String userId = UUID.randomUUID().toString();
        UserEventPayload payload = new UserEventPayload(userId, "test@example.com", "Test User", "USER", "CREATED");
        UserEvent event = UserEvent.of(EventType.USER_CREATED, payload, "test-tenant");

        String json = JsonUtil.toJson(event);
        assertNotNull(json, "Serialized JSON should not be null");
        assertFalse(json.isEmpty(), "Serialized JSON should not be empty");

        UserEvent deserialized = JsonUtil.fromJson(json, UserEvent.class);
        assertNotNull(deserialized, "Deserialized event should not be null");
        assertEquals(event.getEventId(), deserialized.getEventId(), "Event IDs should match");
        assertEquals(EventType.USER_CREATED.getValue(), deserialized.getEventType(), "Event types should match");
        assertEquals("test-tenant", deserialized.getTenantId(), "Tenant IDs should match");
        assertEquals("test@example.com", deserialized.getPayload().getEmail(), "Emails should match");
        assertEquals("Test User", deserialized.getPayload().getFullName(), "Names should match");
        assertEquals(userId, deserialized.getPayload().getUserId(), "User IDs should match");
    }

    @Test
    void testOrderEventSerializationRoundtrip() {
        String orderId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        OrderEventPayload.OrderItemPayload item = new OrderEventPayload.OrderItemPayload(
                "PROD-001", "Widget", 3, new BigDecimal("29.99"));
        OrderEventPayload payload = new OrderEventPayload(orderId, userId, new BigDecimal("89.97"), "PLACED", List.of(item));
        OrderEvent event = OrderEvent.of(EventType.ORDER_PLACED, payload, "test-tenant");

        String json = JsonUtil.toJson(event);
        assertNotNull(json);
        assertTrue(json.contains(orderId), "JSON should contain order ID");
        assertTrue(json.contains("order.placed"), "JSON should contain event type");
        assertTrue(json.contains("89.97"), "JSON should contain total amount");

        OrderEvent deserialized = JsonUtil.fromJson(json, OrderEvent.class);
        assertNotNull(deserialized);
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals("order.placed", deserialized.getEventType());
        assertEquals(orderId, deserialized.getPayload().getOrderId());
        assertEquals(userId, deserialized.getPayload().getUserId());
        assertNotNull(deserialized.getPayload().getItems());
        assertEquals(1, deserialized.getPayload().getItems().size());
        assertEquals("PROD-001", deserialized.getPayload().getItems().get(0).getProductId());
        assertEquals(3, deserialized.getPayload().getItems().get(0).getQuantity());
    }

    @Test
    void testPaymentEventSerializationRoundtrip() {
        String paymentId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        PaymentEventPayload payload = new PaymentEventPayload(
                paymentId, orderId, new BigDecimal("99.50"), "USD", "INITIATED", "GW-ABC12345");
        PaymentEvent event = PaymentEvent.of(EventType.PAYMENT_INITIATED, payload, "test-tenant", "trace-001");

        String json = JsonUtil.toJson(event);
        assertNotNull(json);
        assertTrue(json.contains(paymentId));
        assertTrue(json.contains("payment.initiated"));
        assertTrue(json.contains("GW-ABC12345"));

        PaymentEvent deserialized = JsonUtil.fromJson(json, PaymentEvent.class);
        assertNotNull(deserialized);
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals("payment.initiated", deserialized.getEventType());
        assertEquals(paymentId, deserialized.getPayload().getPaymentId());
        assertEquals(orderId, deserialized.getPayload().getOrderId());
        assertEquals(0, new BigDecimal("99.50").compareTo(deserialized.getPayload().getAmount()));
        assertEquals("USD", deserialized.getPayload().getCurrency());
        assertEquals("GW-ABC12345", deserialized.getPayload().getGatewayRef());
    }

    @Test
    void testUniqueEventIds() {
        UserEventPayload payload = new UserEventPayload(
                UUID.randomUUID().toString(), "dupe@test.com", "Dupe User", "USER", "CREATED");

        UserEvent event1 = UserEvent.of(EventType.USER_CREATED, payload, "tenant-1");
        UserEvent event2 = UserEvent.of(EventType.USER_CREATED, payload, "tenant-1");

        assertFalse(event1.getEventId().equals(event2.getEventId()),
                "Two independently created events must have different IDs");
    }

    @Test
    void testEventContainsTenantId() {
        UserEvent event = UserEvent.of(
                EventType.USER_UPDATED,
                new UserEventPayload("uid-1", "user@test.com", "User", "USER", "UPDATED"),
                "acme-corp"
        );

        assertEquals("acme-corp", event.getTenantId(), "Tenant ID should match");
        assertEquals(EventType.USER_UPDATED.getValue(), event.getEventType(), "Event type should match");
    }

    @Test
    void testEventVersionDefault() {
        UserEvent event = UserEvent.of(
                EventType.USER_CREATED,
                new UserEventPayload("uid-1", "user@test.com", "User", "USER", "CREATED"),
                "tenant-1"
        );

        assertEquals("1.0", event.getVersion(), "Default version should be 1.0");
        assertNotNull(event.getOccurredAt(), "occurredAt should be set");
        assertNotNull(event.getEventId(), "eventId should be set");
        assertFalse(event.getEventId().isEmpty(), "eventId should not be empty");
    }

    @Test
    void testEventTypeValues() {
        assertEquals("user.created", EventType.USER_CREATED.getValue());
        assertEquals("user.updated", EventType.USER_UPDATED.getValue());
        assertEquals("user.deleted", EventType.USER_DELETED.getValue());
        assertEquals("order.placed", EventType.ORDER_PLACED.getValue());
        assertEquals("order.cancelled", EventType.ORDER_CANCELLED.getValue());
        assertEquals("order.completed", EventType.ORDER_COMPLETED.getValue());
        assertEquals("payment.initiated", EventType.PAYMENT_INITIATED.getValue());
        assertEquals("payment.success", EventType.PAYMENT_SUCCESS.getValue());
        assertEquals("payment.failed", EventType.PAYMENT_FAILED.getValue());
    }

    @Test
    void testUserEventJsonContainsAllFields() {
        String userId = UUID.randomUUID().toString();
        UserEvent event = UserEvent.of(
                EventType.USER_CREATED,
                new UserEventPayload(userId, "fields@test.com", "Full Fields User", "ADMIN", "CREATED"),
                "fields-tenant"
        );

        String json = JsonUtil.toJson(event);
        assertTrue(json.contains("eventId"), "JSON should contain eventId field");
        assertTrue(json.contains("eventType"), "JSON should contain eventType field");
        assertTrue(json.contains("tenantId"), "JSON should contain tenantId field");
        assertTrue(json.contains("occurredAt"), "JSON should contain occurredAt field");
        assertTrue(json.contains("version"), "JSON should contain version field");
        assertTrue(json.contains("payload"), "JSON should contain payload field");
        assertTrue(json.contains("fields@test.com"), "JSON should contain email in payload");
        assertTrue(json.contains("Full Fields User"), "JSON should contain fullName in payload");
    }

    @Test
    void testCrossTypeDeserialization() {
        UserEventPayload userPayload = new UserEventPayload("u1", "a@b.com", "A", "USER", "CREATED");
        UserEvent userEvent = UserEvent.of(EventType.USER_CREATED, userPayload, "t1");
        String userJson = JsonUtil.toJson(userEvent);

        OrderEventPayload orderPayload = new OrderEventPayload("o1", "u1", BigDecimal.TEN, "PLACED", null);
        OrderEvent orderEvent = OrderEvent.of(EventType.ORDER_PLACED, orderPayload, "t1");
        String orderJson = JsonUtil.toJson(orderEvent);

        assertTrue(userJson.contains("user.created"), "User event should have user.created type");
        assertTrue(orderJson.contains("order.placed"), "Order event should have order.placed type");

        assertFalse(userJson.contains("order.placed"), "User event JSON should not contain order type");
        assertFalse(orderJson.contains("user.created"), "Order event JSON should not contain user type");
    }
}
