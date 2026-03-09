package com.example.app.service;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.OrderItemEntity;
import com.example.app.domain.UserEntity;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class OrderServiceTest {

    private static final String TEST_TENANT = "order-test-tenant";

    @Inject
    OrderService orderService;

    @Inject
    UserRepository userRepository;

    private UUID testUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        TenantContext.setCurrentTenant(TEST_TENANT);

        String email = "order-user-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFullName("Order Test User");
        user.setRole("USER");
        user.setTenantId(TEST_TENANT);
        user.setStatus(UserEntity.UserStatus.ACTIVE);
        userRepository.persist(user);
        testUserId = user.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void testCreateOrder() {
        List<OrderItemEntity> items = createSampleItems();
        OrderEntity order = orderService.createOrder(testUserId, items);

        assertNotNull(order, "Order should not be null");
        assertNotNull(order.getId(), "Order ID should be generated");
        assertEquals(testUserId, order.getUserId());
        assertEquals(TEST_TENANT, order.getTenantId());
        assertEquals(OrderEntity.OrderStatus.PLACED, order.getStatus());
        assertTrue(order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0, "Total amount should be positive");
        assertEquals(2, order.getItems().size(), "Should have 2 items");
    }

    @Test
    @Transactional
    void testCreateOrderCalculatesTotalCorrectly() {
        OrderItemEntity item1 = new OrderItemEntity();
        item1.setProductId("PROD-1");
        item1.setName("Product 1");
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("10.00"));

        OrderItemEntity item2 = new OrderItemEntity();
        item2.setProductId("PROD-2");
        item2.setName("Product 2");
        item2.setQuantity(3);
        item2.setUnitPrice(new BigDecimal("5.50"));

        List<OrderItemEntity> items = List.of(item1, item2);
        OrderEntity order = orderService.createOrder(testUserId, items);

        BigDecimal expected = new BigDecimal("36.50");
        assertEquals(0, expected.compareTo(order.getTotalAmount()),
                "Total should be (2*10.00) + (3*5.50) = 36.50");
    }

    @Test
    void testCreateOrderWithNullUserId() {
        assertThrows(ValidationException.class,
                () -> orderService.createOrder(null, createSampleItems()));
    }

    @Test
    void testCreateOrderWithEmptyItems() {
        assertThrows(ValidationException.class,
                () -> orderService.createOrder(testUserId, List.of()));
    }

    @Test
    void testCreateOrderWithNullItems() {
        assertThrows(ValidationException.class,
                () -> orderService.createOrder(testUserId, null));
    }

    @Test
    @Transactional
    void testFindOrderById() {
        OrderEntity created = orderService.createOrder(testUserId, createSampleItems());
        OrderEntity found = orderService.findById(created.getId());

        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
    }

    @Test
    void testFindOrderByIdNotFound() {
        assertThrows(NotFoundException.class,
                () -> orderService.findById(UUID.randomUUID()));
    }

    @Test
    @Transactional
    void testUpdateOrderStatus() {
        OrderEntity created = orderService.createOrder(testUserId, createSampleItems());
        OrderEntity cancelled = orderService.updateStatus(created.getId(), OrderEntity.OrderStatus.CANCELLED, "User request");

        assertNotNull(cancelled);
        assertEquals(OrderEntity.OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    @Transactional
    void testOrderStatusTransition() {
        OrderEntity order = orderService.createOrder(testUserId, createSampleItems());
        assertEquals(OrderEntity.OrderStatus.PLACED, order.getStatus());

        OrderEntity confirmed = orderService.updateStatus(order.getId(), OrderEntity.OrderStatus.CONFIRMED, "Payment received");
        assertEquals(OrderEntity.OrderStatus.CONFIRMED, confirmed.getStatus());

        OrderEntity completed = orderService.updateStatus(order.getId(), OrderEntity.OrderStatus.COMPLETED, "Delivered");
        assertEquals(OrderEntity.OrderStatus.COMPLETED, completed.getStatus());
    }

    @Test
    @Transactional
    void testListOrdersByUserId() {
        orderService.createOrder(testUserId, createSampleItems());
        orderService.createOrder(testUserId, createSampleItems());

        List<OrderEntity> orders = orderService.listByUserId(testUserId, 0, 10);
        assertTrue(orders.size() >= 2, "Should find at least 2 orders");
    }

    @Test
    @Transactional
    void testCountByUserId() {
        orderService.createOrder(testUserId, createSampleItems());
        orderService.createOrder(testUserId, createSampleItems());

        long count = orderService.countByUserId(testUserId);
        assertTrue(count >= 2, "Count should be at least 2");
    }

    private List<OrderItemEntity> createSampleItems() {
        List<OrderItemEntity> items = new ArrayList<>();

        OrderItemEntity item1 = new OrderItemEntity();
        item1.setProductId("PROD-001");
        item1.setName("Widget A");
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("29.99"));
        items.add(item1);

        OrderItemEntity item2 = new OrderItemEntity();
        item2.setProductId("PROD-002");
        item2.setName("Widget B");
        item2.setQuantity(1);
        item2.setUnitPrice(new BigDecimal("49.99"));
        items.add(item2);

        return items;
    }
}
