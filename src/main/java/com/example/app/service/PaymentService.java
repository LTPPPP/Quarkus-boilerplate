package com.example.app.service;

import com.example.app.domain.PaymentEntity;
import com.example.app.event.domain.EventType;
import com.example.app.event.domain.PaymentEvent;
import com.example.app.event.domain.PaymentEventPayload;
import com.example.app.event.producer.KafkaEventProducer;
import com.example.app.exception.NotFoundException;
import com.example.app.exception.ValidationException;
import com.example.app.repository.PaymentRepository;
import com.example.app.tenant.context.TenantContext;
import com.example.app.util.TraceContextPropagator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.UUID;

@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    KafkaEventProducer kafkaEventProducer;

    @Inject
    TraceContextPropagator traceContextPropagator;

    public PaymentEntity findById(UUID id) {
        return paymentRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Payment", id.toString()));
    }

    public PaymentEntity findByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Payment", "orderId=" + orderId));
    }

    @Transactional
    public PaymentEntity initiatePayment(UUID orderId, BigDecimal amount, String currency, String method) {
        if (orderId == null) {
            throw new ValidationException("Order ID is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be positive");
        }

        String tenantId = TenantContext.getCurrentTenant();

        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setCurrency(currency != null && !currency.isBlank() ? currency : "USD");
        payment.setMethod(method);
        payment.setStatus(PaymentEntity.PaymentStatus.INITIATED);
        payment.setTenantId(tenantId);
        payment.setGatewayRef("GW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        paymentRepository.persist(payment);
        LOG.infof("Payment initiated: id=%s, orderId=%s, amount=%s %s, tenant=%s",
                payment.getId(), orderId, amount, payment.getCurrency(), tenantId);

        publishPaymentEvent(payment, EventType.PAYMENT_INITIATED, tenantId);
        return payment;
    }

    @Transactional
    public PaymentEntity processRefund(UUID paymentId, BigDecimal refundAmount, String reason) {
        PaymentEntity payment = findById(paymentId);

        if (payment.getStatus() != PaymentEntity.PaymentStatus.SUCCESS) {
            throw new ValidationException(
                    "Can only refund successful payments. Current status: " + payment.getStatus());
        }
        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new ValidationException("Refund amount exceeds payment amount");
        }

        String tenantId = TenantContext.getCurrentTenant();
        payment.setStatus(PaymentEntity.PaymentStatus.REFUNDED);
        paymentRepository.persist(payment);

        LOG.infof("Payment refunded: id=%s, amount=%s, reason=%s, tenant=%s",
                paymentId, refundAmount, reason, tenantId);

        publishPaymentEvent(payment, EventType.PAYMENT_SUCCESS, tenantId);
        return payment;
    }

    private void publishPaymentEvent(PaymentEntity payment, EventType eventType, String tenantId) {
        try {
            PaymentEventPayload payload = new PaymentEventPayload(
                    payment.getId().toString(),
                    payment.getOrderId().toString(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus().name(),
                    payment.getGatewayRef()
            );
            PaymentEvent event = PaymentEvent.of(eventType, payload, tenantId,
                    traceContextPropagator.getCurrentTraceId());
            kafkaEventProducer.publish("payment-events-out", event);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to publish %s event for payment %s", eventType, payment.getId());
        }
    }
}
