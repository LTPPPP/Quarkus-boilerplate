package com.example.app.event.domain;

import com.example.app.event.producer.KafkaEventProducer;
import com.example.app.util.JsonUtil;

import io.quarkus.scheduler.Scheduled;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class OutboxPoller {

    private static final Logger LOG = Logger.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 50;

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    KafkaEventProducer kafkaEventProducer;

    @Scheduled(every = "5s")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPending(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            return;
        }

        LOG.infof("Outbox poller found %d pending events", pendingEvents.size());

        for (OutboxEvent outboxEvent : pendingEvents) {
            try {
                String channel = resolveChannel(outboxEvent.getAggregateType());

                BaseEvent<?> event = deserializeEvent(outboxEvent);
                if (event != null) {
                    kafkaEventProducer.publish(channel, event);
                }

                outboxEventRepository.markProcessed(outboxEvent.getId());
                LOG.infof("Outbox event processed: id=%s, type=%s, aggregate=%s",
                        outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getAggregateType());
            } catch (Exception e) {
                LOG.errorf(e, "Failed to process outbox event: id=%s", outboxEvent.getId());
                outboxEventRepository.markFailed(outboxEvent.getId());
            }
        }
    }

    private String resolveChannel(String aggregateType) {
        if (aggregateType == null) {
            return "notification-events-out";
        }
        return switch (aggregateType.toUpperCase()) {
            case "USER" -> "user-events-out";
            case "ORDER" -> "order-events-out";
            case "PAYMENT" -> "payment-events-out";
            default -> "notification-events-out";
        };
    }

    private BaseEvent<?> deserializeEvent(OutboxEvent outboxEvent) {
        String payload = outboxEvent.getPayload();
        String eventType = outboxEvent.getEventType();

        if (eventType != null && eventType.startsWith("user.")) {
            return JsonUtil.fromJson(payload, com.example.app.event.domain.UserEvent.class);
        }
        if (eventType != null && eventType.startsWith("order.")) {
            return JsonUtil.fromJson(payload, com.example.app.event.domain.OrderEvent.class);
        }
        if (eventType != null && eventType.startsWith("payment.")) {
            return JsonUtil.fromJson(payload, com.example.app.event.domain.PaymentEvent.class);
        }
        LOG.warnf("Could not determine event class for type=%s, attempting generic deserialization", eventType);
        return JsonUtil.fromJson(payload, com.example.app.event.domain.UserEvent.class);
    }
}
