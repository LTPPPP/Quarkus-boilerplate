package com.example.app.service;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.OrderItemEntity;
import com.example.app.event.producer.OrderEventProducer;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.repository.OrderRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceUnitTest {

    private static final String TEST_TENANT = "test-tenant";

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderEventProducer orderEventProducer;

    @InjectMocks
    OrderService orderService;

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
        @DisplayName("should return order when found")
        void findById_found() {
            UUID id = UUID.randomUUID();
            OrderEntity order = createOrder(id);
            when(orderRepository.findByIdOptional(id)).thenReturn(Optional.of(order));

            OrderEntity result = orderService.findById(id);

            assertNotNull(result);
            assertEquals(id, result.getId());
        }

        @Test
        @DisplayName("should throw NotFoundException when not found")
        void findById_notFound() {
            UUID id = UUID.randomUUID();
            when(orderRepository.findByIdOptional(id)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> orderService.findById(id));
        }
    }

    // ---- createOrder ----

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("should create order with valid items and calculate total")
        void createOrder_success() {
            UUID userId = UUID.randomUUID();
            List<OrderItemEntity> items = createSampleItems();

            OrderEntity result = orderService.createOrder(userId, items);

            assertNotNull(result);
            assertEquals(userId, result.getUserId());
            assertEquals(TEST_TENANT, result.getTenantId());
            assertEquals(OrderEntity.OrderStatus.PLACED, result.getStatus());

            // Total = (2 * 10.00) + (3 * 5.50) = 36.50
            assertEquals(0, new BigDecimal("36.50").compareTo(result.getTotalAmount()));

            verify(orderRepository).persist(any(OrderEntity.class));
            verify(orderEventProducer).publishOrderPlaced(any(OrderEntity.class), eq(TEST_TENANT));
        }

        @Test
        @DisplayName("should correctly calculate total with single item")
        void createOrder_singleItem() {
            UUID userId = UUID.randomUUID();
            OrderItemEntity item = new OrderItemEntity();
            item.setProductId("P1");
            item.setName("Product");
            item.setQuantity(5);
            item.setUnitPrice(new BigDecimal("12.50"));

            OrderEntity result = orderService.createOrder(userId, List.of(item));

            assertEquals(0, new BigDecimal("62.50").compareTo(result.getTotalAmount()));
        }

        @Test
        @DisplayName("should throw ValidationException when userId is null")
        void createOrder_nullUserId() {
            assertThrows(ValidationException.class,
                    () -> orderService.createOrder(null, createSampleItems()));
            verify(orderRepository, never()).persist(any(OrderEntity.class));
        }

        @Test
        @DisplayName("should throw ValidationException when items list is null")
        void createOrder_nullItems() {
            assertThrows(ValidationException.class,
                    () -> orderService.createOrder(UUID.randomUUID(), null));
            verify(orderRepository, never()).persist(any(OrderEntity.class));
        }

        @Test
        @DisplayName("should throw ValidationException when items list is empty")
        void createOrder_emptyItems() {
            assertThrows(ValidationException.class,
                    () -> orderService.createOrder(UUID.randomUUID(), List.of()));
            verify(orderRepository, never()).persist(any(OrderEntity.class));
        }

        @Test
        @DisplayName("should still succeed even if event publishing fails")
        void createOrder_eventPublishingFails() {
            UUID userId = UUID.randomUUID();
            doThrow(new RuntimeException("Kafka down"))
                    .when(orderEventProducer).publishOrderPlaced(any(), anyString());

            OrderEntity result = orderService.createOrder(userId, createSampleItems());

            assertNotNull(result);
            verify(orderRepository).persist(any(OrderEntity.class));
        }

        @Test
        @DisplayName("should add all items to the order via addItem")
        void createOrder_itemsAreLinked() {
            UUID userId = UUID.randomUUID();
            List<OrderItemEntity> items = createSampleItems();

            OrderEntity result = orderService.createOrder(userId, items);

            assertEquals(2, result.getItems().size());
            // Verify items are linked to order (addItem sets the back-reference)
            for (OrderItemEntity item : result.getItems()) {
                assertEquals(result, item.getOrder());
            }
        }

        @Test
        @DisplayName("should handle high-precision decimal amounts")
        void createOrder_highPrecisionAmounts() {
            UUID userId = UUID.randomUUID();
            OrderItemEntity item = new OrderItemEntity();
            item.setProductId("P1");
            item.setName("Precise Product");
            item.setQuantity(3);
            item.setUnitPrice(new BigDecimal("33.3333"));

            OrderEntity result = orderService.createOrder(userId, List.of(item));

            assertEquals(0, new BigDecimal("99.9999").compareTo(result.getTotalAmount()));
        }
    }

    // ---- updateStatus ----

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("should update order status")
        void updateStatus_success() {
            UUID id = UUID.randomUUID();
            OrderEntity order = createOrder(id);
            order.setStatus(OrderEntity.OrderStatus.PLACED);
            when(orderRepository.findByIdOptional(id)).thenReturn(Optional.of(order));

            OrderEntity result = orderService.updateStatus(id, OrderEntity.OrderStatus.CONFIRMED, "Payment received");

            assertEquals(OrderEntity.OrderStatus.CONFIRMED, result.getStatus());
            verify(orderRepository).persist(order);
            verify(orderEventProducer).publishOrderStatusChanged(eq(order), eq("PLACED"), eq(TEST_TENANT));
        }

        @Test
        @DisplayName("should cancel order")
        void updateStatus_cancel() {
            UUID id = UUID.randomUUID();
            OrderEntity order = createOrder(id);
            order.setStatus(OrderEntity.OrderStatus.PLACED);
            when(orderRepository.findByIdOptional(id)).thenReturn(Optional.of(order));

            OrderEntity result = orderService.updateStatus(id, OrderEntity.OrderStatus.CANCELLED, "User request");

            assertEquals(OrderEntity.OrderStatus.CANCELLED, result.getStatus());
        }

        @Test
        @DisplayName("should throw NotFoundException when order not found")
        void updateStatus_notFound() {
            UUID id = UUID.randomUUID();
            when(orderRepository.findByIdOptional(id)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                    () -> orderService.updateStatus(id, OrderEntity.OrderStatus.CANCELLED, "reason"));
        }

        @Test
        @DisplayName("should record previous status for event")
        void updateStatus_recordsPreviousStatus() {
            UUID id = UUID.randomUUID();
            OrderEntity order = createOrder(id);
            order.setStatus(OrderEntity.OrderStatus.PROCESSING);
            when(orderRepository.findByIdOptional(id)).thenReturn(Optional.of(order));

            orderService.updateStatus(id, OrderEntity.OrderStatus.COMPLETED, "Done");

            verify(orderEventProducer).publishOrderStatusChanged(
                    any(OrderEntity.class), eq("PROCESSING"), eq(TEST_TENANT));
        }

        @Test
        @DisplayName("should still succeed even if event publishing fails")
        void updateStatus_eventPublishingFails() {
            UUID id = UUID.randomUUID();
            OrderEntity order = createOrder(id);
            order.setStatus(OrderEntity.OrderStatus.PLACED);
            when(orderRepository.findByIdOptional(id)).thenReturn(Optional.of(order));
            doThrow(new RuntimeException("Kafka down"))
                    .when(orderEventProducer).publishOrderStatusChanged(any(), anyString(), anyString());

            OrderEntity result = orderService.updateStatus(id, OrderEntity.OrderStatus.CANCELLED, "reason");

            assertNotNull(result);
            assertEquals(OrderEntity.OrderStatus.CANCELLED, result.getStatus());
        }
    }

    // ---- listByUserId ----

    @Nested
    @DisplayName("listByUserId")
    class ListByUserId {

        @Test
        @DisplayName("should enforce max page size of 100")
        @SuppressWarnings("unchecked")
        void listByUserId_maxPageSize() {
            UUID userId = UUID.randomUUID();
            PanacheQuery<OrderEntity> mockQuery = mock(PanacheQuery.class);
            PanacheQuery<OrderEntity> pagedQuery = mock(PanacheQuery.class);
            when(orderRepository.find("userId = ?1 and tenantId = ?2", userId, TEST_TENANT))
                    .thenReturn(mockQuery);
            when(mockQuery.page(any(Page.class))).thenReturn(pagedQuery);
            when(pagedQuery.list()).thenReturn(List.of());

            orderService.listByUserId(userId, 0, 500);

            ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
            verify(mockQuery).page(pageCaptor.capture());
            assertEquals(100, pageCaptor.getValue().size);
        }

        @Test
        @DisplayName("should default page size to 20 when size is 0 or negative")
        @SuppressWarnings("unchecked")
        void listByUserId_defaultPageSize() {
            UUID userId = UUID.randomUUID();
            PanacheQuery<OrderEntity> mockQuery = mock(PanacheQuery.class);
            PanacheQuery<OrderEntity> pagedQuery = mock(PanacheQuery.class);
            when(orderRepository.find("userId = ?1 and tenantId = ?2", userId, TEST_TENANT))
                    .thenReturn(mockQuery);
            when(mockQuery.page(any(Page.class))).thenReturn(pagedQuery);
            when(pagedQuery.list()).thenReturn(List.of());

            orderService.listByUserId(userId, 0, -1);

            ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
            verify(mockQuery).page(pageCaptor.capture());
            assertEquals(20, pageCaptor.getValue().size);
        }
    }

    // ---- countByUserId ----

    @Test
    @DisplayName("countByUserId should delegate to repository with tenant context")
    void countByUserId() {
        UUID userId = UUID.randomUUID();
        when(orderRepository.count("userId = ?1 and tenantId = ?2", userId, TEST_TENANT))
                .thenReturn(5L);

        long count = orderService.countByUserId(userId);

        assertEquals(5L, count);
    }

    // ---- helpers ----

    private OrderEntity createOrder(UUID id) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setUserId(UUID.randomUUID());
        order.setTenantId(TEST_TENANT);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(OrderEntity.OrderStatus.PLACED);
        return order;
    }

    private List<OrderItemEntity> createSampleItems() {
        List<OrderItemEntity> items = new ArrayList<>();

        OrderItemEntity item1 = new OrderItemEntity();
        item1.setProductId("PROD-1");
        item1.setName("Product 1");
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("10.00"));
        items.add(item1);

        OrderItemEntity item2 = new OrderItemEntity();
        item2.setProductId("PROD-2");
        item2.setName("Product 2");
        item2.setQuantity(3);
        item2.setUnitPrice(new BigDecimal("5.50"));
        items.add(item2);

        return items;
    }
}
