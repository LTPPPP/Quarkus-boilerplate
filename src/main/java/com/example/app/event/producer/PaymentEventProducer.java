package com.example.app.event.producer;

import com.example.app.domain.PaymentEntity;
import com.example.app.event.domain.EventType;
import com.example.app.event.domain.PaymentEvent;
import com.example.app.event.domain.PaymentEventPayload;
import com.example.app.util.TraceContextPropagator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

@ApplicationScoped
public class PaymentEventProducer {

    private static final Logger LOG = Logger.getLogger(PaymentEventProducer.class);
    private static final String CHANNEL = "payment-events-out";

    @Inject
    KafkaEventProducer kafkaEventProducer;

    @Inject
    TraceContextPropagator traceContextPropagator;

    public void publishPaymentInitiated(PaymentEntity payment, String tenantId) {
        PaymentEventPayload payload = buildPayload(payment);
        PaymentEvent event = PaymentEvent.of(EventType.PAYMENT_INITIATED, payload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        LOG.infof("Publishing PAYMENT_INITIATED event for payment %s in tenant %s", payment.getId(), tenantId);
        kafkaEventProducer.publish(CHANNEL, event);
    }

    public void publishPaymentSuccess(PaymentEntity payment, String tenantId) {
        PaymentEventPayload payload = buildPayload(payment);
        PaymentEvent event = PaymentEvent.of(EventType.PAYMENT_SUCCESS, payload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        LOG.infof("Publishing PAYMENT_SUCCESS event for payment %s in tenant %s", payment.getId(), tenantId);
        kafkaEventProducer.publish(CHANNEL, event);
    }

    public void publishPaymentFailed(PaymentEntity payment, String tenantId) {
        PaymentEventPayload payload = buildPayload(payment);
        PaymentEvent event = PaymentEvent.of(EventType.PAYMENT_FAILED, payload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        LOG.infof("Publishing PAYMENT_FAILED event for payment %s in tenant %s", payment.getId(), tenantId);
        kafkaEventProducer.publish(CHANNEL, event);
    }

    private PaymentEventPayload buildPayload(PaymentEntity payment) {
        return new PaymentEventPayload(
                payment.getId().toString(),
                payment.getOrderId().toString(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getGatewayRef()
        );
    }
}
