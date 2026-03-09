package com.example.app.event.consumer;

import com.example.app.event.domain.EventType;
import com.example.app.event.domain.PaymentEvent;
import com.example.app.event.domain.PaymentEventPayload;
import com.example.app.event.handler.PaymentEventHandler;
import com.example.app.tenant.context.TenantContext;
import com.example.app.util.JsonUtil;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class PaymentEventConsumer {

    private static final Logger LOG = Logger.getLogger(PaymentEventConsumer.class);
    private static final String IDEMPOTENCY_PREFIX = "event:processed:payment:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ValueCommands<String, String> redisValues;

    @Inject
    PaymentEventHandler paymentEventHandler;

    public PaymentEventConsumer(RedisDataSource redisDataSource) {
        this.redisValues = redisDataSource.value(String.class);
    }

    @Incoming("payment-events-in")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<Void> processPaymentEvent(Message<String> message) {
        String json = message.getPayload();
        PaymentEvent event = null;
        try {
            event = JsonUtil.fromJson(json, PaymentEvent.class);
            LOG.infof("Received payment event [%s], eventId=%s, tenant=%s",
                    event.getEventType(), event.getEventId(), event.getTenantId());

            String idempotencyKey = IDEMPOTENCY_PREFIX + event.getEventId();
            String existing = redisValues.get(idempotencyKey);
            if (existing != null) {
                LOG.infof("Duplicate payment event detected, skipping eventId=%s", event.getEventId());
                return message.ack();
            }

            if (event.getTenantId() != null) {
                TenantContext.setCurrentTenant(event.getTenantId());
            }

            PaymentEventPayload payload = event.getPayload();
            String eventTypeValue = event.getEventType();

            if (EventType.PAYMENT_INITIATED.getValue().equals(eventTypeValue)) {
                paymentEventHandler.onPaymentInitiated(payload, event.getTenantId());
            } else if (EventType.PAYMENT_SUCCESS.getValue().equals(eventTypeValue)) {
                paymentEventHandler.onPaymentSuccess(payload, event.getTenantId());
            } else if (EventType.PAYMENT_FAILED.getValue().equals(eventTypeValue)) {
                paymentEventHandler.onPaymentFailed(payload, event.getTenantId());
            } else {
                LOG.warnf("Unknown payment event type: %s", eventTypeValue);
            }

            redisValues.setex(idempotencyKey, IDEMPOTENCY_TTL.getSeconds(), "1");
            LOG.infof("Successfully processed payment event [%s], eventId=%s", eventTypeValue, event.getEventId());
            return message.ack();
        } catch (Exception e) {
            String eventId = event != null ? event.getEventId() : "unknown";
            LOG.errorf(e, "Failed to process payment event, eventId=%s", eventId);
            return message.nack(e);
        } finally {
            TenantContext.clear();
        }
    }
}
