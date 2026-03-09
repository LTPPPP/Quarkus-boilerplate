package com.example.app.event.handler;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.PaymentEntity;
import com.example.app.event.domain.PaymentEventPayload;
import com.example.app.repository.OrderRepository;
import com.example.app.repository.PaymentRepository;
import com.example.app.tenant.service.TenantAwareCache;
import com.example.app.util.JsonUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PaymentEventHandler {

    private static final Logger LOG = Logger.getLogger(PaymentEventHandler.class);
    private static final Duration PAYMENT_STATUS_TTL = Duration.ofHours(1);
    private static final Duration NOTIFICATION_TTL = Duration.ofDays(7);

    @Inject
    TenantAwareCache tenantAwareCache;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    OrderRepository orderRepository;

    public void onPaymentInitiated(PaymentEventPayload payload, String tenantId) {
        LOG.infof("Handling PAYMENT_INITIATED: paymentId=%s, orderId=%s, amount=%s %s, tenant=%s",
                payload.getPaymentId(), payload.getOrderId(), payload.getAmount(),
                payload.getCurrency(), tenantId);

        String cacheKey = "payment:status:" + payload.getPaymentId();
        tenantAwareCache.put(cacheKey, "INITIATED", Duration.ofMinutes(30));

        processGatewayInitiation(payload, tenantId);

        cachePaymentTimeline(payload, "INITIATED", tenantId);

        LOG.infof("PAYMENT_INITIATED handling complete for payment %s", payload.getPaymentId());
    }

    @Transactional
    public void onPaymentSuccess(PaymentEventPayload payload, String tenantId) {
        LOG.infof("Handling PAYMENT_SUCCESS: paymentId=%s, orderId=%s, amount=%s %s, tenant=%s",
                payload.getPaymentId(), payload.getOrderId(), payload.getAmount(),
                payload.getCurrency(), tenantId);

        String cacheKey = "payment:status:" + payload.getPaymentId();
        tenantAwareCache.put(cacheKey, "SUCCESS", PAYMENT_STATUS_TTL);

        updatePaymentEntityStatus(payload.getPaymentId(), PaymentEntity.PaymentStatus.SUCCESS);
        updateOrderPaymentStatus(payload, tenantId);
        sendPaymentConfirmationNotification(payload, tenantId);
        cachePaymentTimeline(payload, "SUCCESS", tenantId);

        LOG.infof("PAYMENT_SUCCESS handling complete for payment %s", payload.getPaymentId());
    }

    @Transactional
    public void onPaymentFailed(PaymentEventPayload payload, String tenantId) {
        LOG.infof("Handling PAYMENT_FAILED: paymentId=%s, orderId=%s, gateway=%s, tenant=%s",
                payload.getPaymentId(), payload.getOrderId(), payload.getGatewayRef(), tenantId);

        String cacheKey = "payment:status:" + payload.getPaymentId();
        tenantAwareCache.put(cacheKey, "FAILED", PAYMENT_STATUS_TTL);

        updatePaymentEntityStatus(payload.getPaymentId(), PaymentEntity.PaymentStatus.FAILED);
        sendPaymentFailureNotification(payload, tenantId);
        cachePaymentTimeline(payload, "FAILED", tenantId);

        updateOrderOnPaymentFailure(payload, tenantId);

        LOG.infof("PAYMENT_FAILED handling complete for payment %s", payload.getPaymentId());
    }

    private void processGatewayInitiation(PaymentEventPayload payload, String tenantId) {
        Map<String, Object> gatewayRequest = new HashMap<>();
        gatewayRequest.put("paymentId", payload.getPaymentId());
        gatewayRequest.put("orderId", payload.getOrderId());
        gatewayRequest.put("amount", payload.getAmount());
        gatewayRequest.put("currency", payload.getCurrency() != null ? payload.getCurrency() : "USD");
        gatewayRequest.put("gatewayRef", payload.getGatewayRef());
        gatewayRequest.put("initiatedAt", Instant.now().toString());
        gatewayRequest.put("tenantId", tenantId);

        String gatewayKey = "payment:gateway:" + payload.getPaymentId();
        tenantAwareCache.put(gatewayKey, JsonUtil.toJson(gatewayRequest), Duration.ofMinutes(30));

        LOG.infof("Payment gateway request cached for payment %s (ref: %s, amount: %s %s)",
                payload.getPaymentId(), payload.getGatewayRef(), payload.getAmount(), payload.getCurrency());
    }

    private void sendPaymentConfirmationNotification(PaymentEventPayload payload, String tenantId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "PAYMENT_SUCCESS");
        notification.put("paymentId", payload.getPaymentId());
        notification.put("orderId", payload.getOrderId());
        notification.put("amount", payload.getAmount());
        notification.put("currency", payload.getCurrency());
        notification.put("gatewayRef", payload.getGatewayRef());
        notification.put("tenantId", tenantId);
        notification.put("confirmedAt", Instant.now().toString());

        String notifKey = "notification:payment:success:" + payload.getPaymentId();
        tenantAwareCache.put(notifKey, JsonUtil.toJson(notification), NOTIFICATION_TTL);

        LOG.infof("Payment confirmation notification queued for payment %s, order %s",
                payload.getPaymentId(), payload.getOrderId());
    }

    private void updateOrderPaymentStatus(PaymentEventPayload payload, String tenantId) {
        try {
            UUID orderId = UUID.fromString(payload.getOrderId());
            orderRepository.findByIdOptional(orderId).ifPresent(order -> {
                if (order.getStatus() == OrderEntity.OrderStatus.PLACED ||
                        order.getStatus() == OrderEntity.OrderStatus.PENDING) {
                    order.setStatus(OrderEntity.OrderStatus.CONFIRMED);
                    orderRepository.persist(order);
                    LOG.infof("Order %s status updated to CONFIRMED after payment %s",
                            payload.getOrderId(), payload.getPaymentId());
                }
            });

            String orderStatusKey = "order:status:" + payload.getOrderId();
            tenantAwareCache.put(orderStatusKey, "CONFIRMED", Duration.ofHours(24));
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update order payment status for order %s", payload.getOrderId());
        }
    }

    private void updateOrderOnPaymentFailure(PaymentEventPayload payload, String tenantId) {
        try {
            UUID orderId = UUID.fromString(payload.getOrderId());
            orderRepository.findByIdOptional(orderId).ifPresent(order -> {
                if (order.getStatus() == OrderEntity.OrderStatus.PLACED ||
                        order.getStatus() == OrderEntity.OrderStatus.PENDING) {
                    order.setStatus(OrderEntity.OrderStatus.PAYMENT_FAILED);
                    orderRepository.persist(order);
                    LOG.infof("Order %s marked as PAYMENT_FAILED due to payment %s failure",
                            payload.getOrderId(), payload.getPaymentId());
                }
            });

            String orderStatusKey = "order:status:" + payload.getOrderId();
            tenantAwareCache.put(orderStatusKey, "PAYMENT_FAILED", Duration.ofHours(24));
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update order on payment failure for order %s", payload.getOrderId());
        }
    }

    private void sendPaymentFailureNotification(PaymentEventPayload payload, String tenantId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "PAYMENT_FAILED");
        notification.put("paymentId", payload.getPaymentId());
        notification.put("orderId", payload.getOrderId());
        notification.put("amount", payload.getAmount());
        notification.put("currency", payload.getCurrency());
        notification.put("gatewayRef", payload.getGatewayRef());
        notification.put("tenantId", tenantId);
        notification.put("failedAt", Instant.now().toString());
        notification.put("retryEligible", true);

        String notifKey = "notification:payment:failed:" + payload.getPaymentId();
        tenantAwareCache.put(notifKey, JsonUtil.toJson(notification), NOTIFICATION_TTL);

        LOG.infof("Payment failure notification queued for payment %s, order %s",
                payload.getPaymentId(), payload.getOrderId());
    }

    private void updatePaymentEntityStatus(String paymentId, PaymentEntity.PaymentStatus status) {
        try {
            UUID id = UUID.fromString(paymentId);
            paymentRepository.findByIdOptional(id).ifPresent(payment -> {
                payment.setStatus(status);
                paymentRepository.persist(payment);
                LOG.infof("Payment entity %s status updated to %s", paymentId, status);
            });
        } catch (Exception e) {
            LOG.warnf(e, "Failed to update payment entity status for paymentId=%s", paymentId);
        }
    }

    private void cachePaymentTimeline(PaymentEventPayload payload, String status, String tenantId) {
        String timelineKey = "payment:timeline:" + payload.getPaymentId();
        String existingTimeline = tenantAwareCache.get(timelineKey);

        Map<String, Object> timelineEntry = Map.of(
                "status", status,
                "timestamp", Instant.now().toString(),
                "amount", payload.getAmount(),
                "gatewayRef", payload.getGatewayRef() != null ? payload.getGatewayRef() : ""
        );

        String updatedTimeline;
        if (existingTimeline != null) {
            updatedTimeline = existingTimeline.substring(0, existingTimeline.length() - 1)
                    + "," + JsonUtil.toJson(timelineEntry) + "]";
        } else {
            updatedTimeline = "[" + JsonUtil.toJson(timelineEntry) + "]";
        }

        tenantAwareCache.put(timelineKey, updatedTimeline, Duration.ofDays(30));
    }
}
