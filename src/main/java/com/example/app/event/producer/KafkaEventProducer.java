package com.example.app.event.producer;

import com.example.app.event.domain.BaseEvent;
import com.example.app.util.JsonUtil;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class KafkaEventProducer {

    private static final Logger LOG = Logger.getLogger(KafkaEventProducer.class);

    @Inject
    @Channel("user-events-out")
    MutinyEmitter<Record<String, String>> userEventsEmitter;

    @Inject
    @Channel("order-events-out")
    MutinyEmitter<Record<String, String>> orderEventsEmitter;

    @Inject
    @Channel("payment-events-out")
    MutinyEmitter<Record<String, String>> paymentEventsEmitter;

    @Inject
    @Channel("notification-events-out")
    MutinyEmitter<Record<String, String>> notificationEventsEmitter;

    public void publish(String channel, BaseEvent<?> event) {
        try {
            String json = JsonUtil.toJson(event);
            String key = event.getTenantId() != null ? event.getTenantId() : "default";
            Record<String, String> record = Record.of(key, json);

            MutinyEmitter<Record<String, String>> emitter = resolveEmitter(channel);
            emitter.sendAndAwait(record);

            LOG.infof("Published event [%s] to channel [%s] with key [%s], eventId=%s",
                    event.getEventType(), channel, key, event.getEventId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to publish event [%s] to channel [%s], eventId=%s",
                    event.getEventType(), channel, event.getEventId());
            throw new RuntimeException("Event publishing failed", e);
        }
    }

    public Uni<Void> publishAsync(String channel, BaseEvent<?> event) {
        try {
            String json = JsonUtil.toJson(event);
            String key = event.getTenantId() != null ? event.getTenantId() : "default";
            Record<String, String> record = Record.of(key, json);

            MutinyEmitter<Record<String, String>> emitter = resolveEmitter(channel);

            return emitter.send(record)
                    .invoke(() -> LOG.infof("Async published event [%s] to channel [%s], eventId=%s",
                            event.getEventType(), channel, event.getEventId()))
                    .onFailure().invoke(t -> LOG.errorf(t, "Async publish failed for event [%s] to channel [%s], eventId=%s",
                            event.getEventType(), channel, event.getEventId()));
        } catch (Exception e) {
            LOG.errorf(e, "Failed to serialize event for async publish, eventId=%s", event.getEventId());
            return Uni.createFrom().failure(e);
        }
    }

    public void publishBatch(String channel, List<BaseEvent<?>> events) {
        LOG.infof("Publishing batch of %d events to channel [%s]", events.size(), channel);
        for (BaseEvent<?> event : events) {
            publish(channel, event);
        }
        LOG.infof("Batch publish complete for channel [%s], count=%d", channel, events.size());
    }

    private MutinyEmitter<Record<String, String>> resolveEmitter(String channel) {
        return switch (channel) {
            case "user-events-out" -> userEventsEmitter;
            case "order-events-out" -> orderEventsEmitter;
            case "payment-events-out" -> paymentEventsEmitter;
            case "notification-events-out" -> notificationEventsEmitter;
            default -> throw new IllegalArgumentException("Unknown channel: " + channel);
        };
    }
}
