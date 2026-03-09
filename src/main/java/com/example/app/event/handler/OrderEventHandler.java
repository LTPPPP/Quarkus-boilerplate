package com.example.app.event.handler;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.PaymentEntity;
import com.example.app.event.domain.EventType;
import com.example.app.event.domain.OrderEventPayload;
import com.example.app.event.domain.PaymentEvent;
import com.example.app.event.domain.PaymentEventPayload;
import com.example.app.event.producer.KafkaEventProducer;
import com.example.app.grpc.client.PaymentGrpcClient;
import com.example.app.grpc.client.UserGrpcClient;
import com.example.app.grpc.proto.PaymentProto;
import com.example.app.grpc.proto.UserProto;
import com.example.app.repository.OrderRepository;
import com.example.app.repository.PaymentRepository;
import com.example.app.tenant.service.TenantAwareCache;
import com.example.app.util.JsonUtil;
import com.example.app.util.TraceContextPropagator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OrderEventHandler {

    private static final Logger LOG = Logger.getLogger(OrderEventHandler.class);
    private static final int LOYALTY_POINTS_PER_DOLLAR = 10;
    private static final Duration INVENTORY_LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration RECEIPT_TTL = Duration.ofDays(90);

    @Inject
    KafkaEventProducer kafkaEventProducer;

    @Inject
    TraceContextPropagator traceContextPropagator;

    @Inject
    UserGrpcClient userGrpcClient;

    @Inject
    PaymentGrpcClient paymentGrpcClient;

    @Inject
    TenantAwareCache tenantAwareCache;

    @Inject
    OrderRepository orderRepository;

    @Inject
    PaymentRepository paymentRepository;

    public void onOrderPlaced(OrderEventPayload payload, String tenantId) {
        LOG.infof("Handling ORDER_PLACED for order %s in tenant %s", payload.getOrderId(), tenantId);

        Optional<UserProto> userOpt = userGrpcClient.getUserById(payload.getUserId());
        userOpt.ifPresent(user ->
                LOG.infof("Order placed by user: %s (%s)", user.getFullName(), user.getEmail()));

        reserveInventory(payload, tenantId);
        cacheOrderStatus(payload, "PLACED", tenantId);

        try {
            PaymentProto paymentResult = paymentGrpcClient.initiatePayment(
                    payload.getOrderId(),
                    payload.getTotalAmount().doubleValue(),
                    "USD"
            );
            LOG.infof("Payment initiated via gRPC for order %s: paymentId=%s, status=%s",
                    payload.getOrderId(), paymentResult.getId(), paymentResult.getStatus());
        } catch (Exception e) {
            LOG.warnf(e, "gRPC payment call failed for order %s, falling back to Kafka event", payload.getOrderId());
            triggerPaymentViaKafka(payload, tenantId);
        }

        sendOrderConfirmationNotification(payload, userOpt.orElse(null), tenantId);

        LOG.infof("ORDER_PLACED handling complete for order %s", payload.getOrderId());
    }

    @Transactional
    public void onOrderCancelled(OrderEventPayload payload, String tenantId) {
        LOG.infof("Handling ORDER_CANCELLED for order %s in tenant %s", payload.getOrderId(), tenantId);

        releaseInventory(payload, tenantId);
        initiateRefund(payload, tenantId);
        cacheOrderStatus(payload, "CANCELLED", tenantId);

        updateOrderEntityStatus(payload.getOrderId(), OrderEntity.OrderStatus.CANCELLED);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ORDER_CANCELLED");
        notification.put("orderId", payload.getOrderId());
        notification.put("userId", payload.getUserId());
        notification.put("amount", payload.getTotalAmount());
        notification.put("tenantId", tenantId);

        String notifKey = "notification:order:cancelled:" + payload.getOrderId();
        tenantAwareCache.put(notifKey, JsonUtil.toJson(notification), Duration.ofDays(7));

        LOG.infof("ORDER_CANCELLED handling complete for order %s", payload.getOrderId());
    }

    @Transactional
    public void onOrderCompleted(OrderEventPayload payload, String tenantId) {
        LOG.infof("Handling ORDER_COMPLETED for order %s in tenant %s", payload.getOrderId(), tenantId);

        updateLoyaltyPoints(payload, tenantId);
        generateReceipt(payload, tenantId);
        cacheOrderStatus(payload, "COMPLETED", tenantId);

        updateOrderEntityStatus(payload.getOrderId(), OrderEntity.OrderStatus.COMPLETED);

        releaseInventoryLocks(payload, tenantId);

        LOG.infof("ORDER_COMPLETED handling complete for order %s", payload.getOrderId());
    }

    private void reserveInventory(OrderEventPayload payload, String tenantId) {
        if (payload.getItems() == null || payload.getItems().isEmpty()) return;

        for (var item : payload.getItems()) {
            String lockKey = "inventory:lock:" + item.getProductId();
            String lockValue = JsonUtil.toJson(Map.of(
                    "orderId", payload.getOrderId(),
                    "quantity", item.getQuantity(),
                    "reservedAt", Instant.now().toString()
            ));
            tenantAwareCache.put(lockKey, lockValue, INVENTORY_LOCK_TTL);

            String stockKey = "inventory:stock:" + item.getProductId();
            String currentStock = tenantAwareCache.get(stockKey);
            if (currentStock != null) {
                int stock = Integer.parseInt(currentStock);
                int remaining = stock - item.getQuantity();
                tenantAwareCache.put(stockKey, String.valueOf(Math.max(0, remaining)), Duration.ofDays(1));
                LOG.infof("Inventory reserved: %d x %s (remaining: %d) for order %s",
                        item.getQuantity(), item.getProductId(), Math.max(0, remaining), payload.getOrderId());
            } else {
                LOG.infof("Inventory reserved (no stock tracking): %d x %s for order %s",
                        item.getQuantity(), item.getProductId(), payload.getOrderId());
            }
        }
    }

    private void releaseInventory(OrderEventPayload payload, String tenantId) {
        if (payload.getItems() == null || payload.getItems().isEmpty()) return;

        for (var item : payload.getItems()) {
            tenantAwareCache.evict("inventory:lock:" + item.getProductId());

            String stockKey = "inventory:stock:" + item.getProductId();
            String currentStock = tenantAwareCache.get(stockKey);
            if (currentStock != null) {
                int stock = Integer.parseInt(currentStock);
                int restored = stock + item.getQuantity();
                tenantAwareCache.put(stockKey, String.valueOf(restored), Duration.ofDays(1));
                LOG.infof("Inventory released: %d x %s (restored to: %d) for cancelled order %s",
                        item.getQuantity(), item.getProductId(), restored, payload.getOrderId());
            } else {
                LOG.infof("Inventory released (no stock tracking): %d x %s for order %s",
                        item.getQuantity(), item.getProductId(), payload.getOrderId());
            }
        }
    }

    private void releaseInventoryLocks(OrderEventPayload payload, String tenantId) {
        if (payload.getItems() == null) return;
        for (var item : payload.getItems()) {
            tenantAwareCache.evict("inventory:lock:" + item.getProductId());
        }
    }

    private void initiateRefund(OrderEventPayload payload, String tenantId) {
        try {
            UUID orderId = UUID.fromString(payload.getOrderId());
            Optional<PaymentEntity> paymentOpt = paymentRepository.findByOrderId(orderId);

            if (paymentOpt.isPresent()) {
                PaymentEntity payment = paymentOpt.get();
                if (payment.getStatus() == PaymentEntity.PaymentStatus.SUCCESS) {
                    payment.setStatus(PaymentEntity.PaymentStatus.REFUNDED);
                    paymentRepository.persist(payment);

                    PaymentEventPayload refundPayload = new PaymentEventPayload(
                            payment.getId().toString(),
                            payload.getOrderId(),
                            payment.getAmount(),
                            payment.getCurrency(),
                            "REFUNDED",
                            payment.getGatewayRef()
                    );
                    PaymentEvent refundEvent = PaymentEvent.of(EventType.PAYMENT_FAILED, refundPayload, tenantId,
                            traceContextPropagator.getCurrentTraceId());
                    kafkaEventProducer.publish("payment-events-out", refundEvent);

                    LOG.infof("Refund initiated for order %s: paymentId=%s, amount=%s %s",
                            payload.getOrderId(), payment.getId(), payment.getAmount(), payment.getCurrency());
                } else {
                    LOG.infof("No refund needed for order %s: payment status is %s",
                            payload.getOrderId(), payment.getStatus());
                }
            } else {
                LOG.infof("No payment found for cancelled order %s, skipping refund", payload.getOrderId());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process refund for order %s", payload.getOrderId());
        }
    }

    private void triggerPaymentViaKafka(OrderEventPayload payload, String tenantId) {
        PaymentEventPayload paymentPayload = new PaymentEventPayload(
                UUID.randomUUID().toString(),
                payload.getOrderId(),
                payload.getTotalAmount(),
                "USD",
                "INITIATED",
                null
        );

        PaymentEvent paymentEvent = PaymentEvent.of(EventType.PAYMENT_INITIATED, paymentPayload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        kafkaEventProducer.publish("payment-events-out", paymentEvent);
        LOG.infof("Payment initiated via Kafka for order %s, amount=%s",
                payload.getOrderId(), payload.getTotalAmount());
    }

    private void updateLoyaltyPoints(OrderEventPayload payload, String tenantId) {
        int points = payload.getTotalAmount()
                .setScale(0, RoundingMode.FLOOR)
                .intValue() * LOYALTY_POINTS_PER_DOLLAR;

        String loyaltyKey = "loyalty:points:" + payload.getUserId();
        String currentPointsStr = tenantAwareCache.get(loyaltyKey);
        int currentPoints = currentPointsStr != null ? Integer.parseInt(currentPointsStr) : 0;
        int newTotal = currentPoints + points;

        tenantAwareCache.put(loyaltyKey, String.valueOf(newTotal), Duration.ofDays(365));

        String historyKey = "loyalty:history:" + payload.getUserId() + ":" + payload.getOrderId();
        Map<String, Object> historyEntry = Map.of(
                "orderId", payload.getOrderId(),
                "pointsEarned", points,
                "orderAmount", payload.getTotalAmount(),
                "earnedAt", Instant.now().toString()
        );
        tenantAwareCache.put(historyKey, JsonUtil.toJson(historyEntry), Duration.ofDays(365));

        LOG.infof("Loyalty points awarded: +%d points for user %s (total: %d) on order %s",
                points, payload.getUserId(), newTotal, payload.getOrderId());
    }

    private void generateReceipt(OrderEventPayload payload, String tenantId) {
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("receiptId", "RCP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        receipt.put("orderId", payload.getOrderId());
        receipt.put("userId", payload.getUserId());
        receipt.put("totalAmount", payload.getTotalAmount());
        receipt.put("currency", "USD");
        receipt.put("status", "COMPLETED");
        receipt.put("issuedAt", Instant.now().toString());
        receipt.put("tenantId", tenantId);

        if (payload.getItems() != null) {
            receipt.put("itemCount", payload.getItems().size());
            receipt.put("items", payload.getItems().stream().map(item -> Map.of(
                    "productId", item.getProductId(),
                    "name", item.getName(),
                    "quantity", item.getQuantity(),
                    "unitPrice", item.getUnitPrice(),
                    "subtotal", item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
            )).toList());
        }

        String receiptKey = "receipt:" + payload.getOrderId();
        tenantAwareCache.put(receiptKey, JsonUtil.toJson(receipt), RECEIPT_TTL);

        LOG.infof("Receipt generated: %s for order %s, amount=%s",
                receipt.get("receiptId"), payload.getOrderId(), payload.getTotalAmount());
    }

    private void sendOrderConfirmationNotification(OrderEventPayload payload, UserProto user, String tenantId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ORDER_PLACED");
        notification.put("orderId", payload.getOrderId());
        notification.put("userId", payload.getUserId());
        notification.put("amount", payload.getTotalAmount());
        notification.put("tenantId", tenantId);
        if (user != null) {
            notification.put("recipientEmail", user.getEmail());
            notification.put("recipientName", user.getFullName());
        }

        String notifKey = "notification:order:placed:" + payload.getOrderId();
        tenantAwareCache.put(notifKey, JsonUtil.toJson(notification), Duration.ofDays(7));

        LOG.infof("Order confirmation notification queued for order %s", payload.getOrderId());
    }

    private void cacheOrderStatus(OrderEventPayload payload, String status, String tenantId) {
        String statusKey = "order:status:" + payload.getOrderId();
        tenantAwareCache.put(statusKey, status, Duration.ofHours(24));
    }

    private void updateOrderEntityStatus(String orderId, OrderEntity.OrderStatus status) {
        try {
            UUID id = UUID.fromString(orderId);
            orderRepository.findByIdOptional(id).ifPresent(order -> {
                order.setStatus(status);
                orderRepository.persist(order);
            });
        } catch (Exception e) {
            LOG.warnf(e, "Failed to update order entity status for orderId=%s", orderId);
        }
    }
}
