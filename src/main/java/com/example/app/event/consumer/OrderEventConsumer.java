package com.example.app.event.consumer;

import com.example.app.event.domain.EventType;
import com.example.app.event.domain.OrderEvent;
import com.example.app.event.domain.OrderEventPayload;
import com.example.app.event.handler.OrderEventHandler;
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
public class OrderEventConsumer {

    private static final Logger LOG = Logger.getLogger(OrderEventConsumer.class);
    private static final String IDEMPOTENCY_PREFIX = "event:processed:order:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ValueCommands<String, String> redisValues;

    @Inject
    OrderEventHandler orderEventHandler;

    public OrderEventConsumer(RedisDataSource redisDataSource) {
        this.redisValues = redisDataSource.value(String.class);
    }

    @Incoming("order-events-in")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<Void> processOrderEvent(Message<String> message) {
        String json = message.getPayload();
        OrderEvent event = null;
        try {
            event = JsonUtil.fromJson(json, OrderEvent.class);
            LOG.infof("Received order event [%s], eventId=%s, tenant=%s",
                    event.getEventType(), event.getEventId(), event.getTenantId());

            String idempotencyKey = IDEMPOTENCY_PREFIX + event.getEventId();
            String existing = redisValues.get(idempotencyKey);
            if (existing != null) {
                LOG.infof("Duplicate order event detected, skipping eventId=%s", event.getEventId());
                return message.ack();
            }

            if (event.getTenantId() != null) {
                TenantContext.setCurrentTenant(event.getTenantId());
            }

            OrderEventPayload payload = event.getPayload();
            String eventTypeValue = event.getEventType();

            if (EventType.ORDER_PLACED.getValue().equals(eventTypeValue)) {
                orderEventHandler.onOrderPlaced(payload, event.getTenantId());
            } else if (EventType.ORDER_CANCELLED.getValue().equals(eventTypeValue)) {
                orderEventHandler.onOrderCancelled(payload, event.getTenantId());
            } else if (EventType.ORDER_COMPLETED.getValue().equals(eventTypeValue)) {
                orderEventHandler.onOrderCompleted(payload, event.getTenantId());
            } else {
                LOG.warnf("Unknown order event type: %s", eventTypeValue);
            }

            redisValues.setex(idempotencyKey, IDEMPOTENCY_TTL.getSeconds(), "1");
            LOG.infof("Successfully processed order event [%s], eventId=%s", eventTypeValue, event.getEventId());
            return message.ack();
        } catch (Exception e) {
            String eventId = event != null ? event.getEventId() : "unknown";
            LOG.errorf(e, "Failed to process order event, eventId=%s", eventId);
            return message.nack(e);
        } finally {
            TenantContext.clear();
        }
    }
}
