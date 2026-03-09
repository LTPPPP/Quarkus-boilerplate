package com.example.app.event.producer;

import com.example.app.domain.UserEntity;
import com.example.app.event.domain.EventType;
import com.example.app.event.domain.UserEvent;
import com.example.app.event.domain.UserEventPayload;
import com.example.app.util.TraceContextPropagator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class UserEventProducer {

    private static final Logger LOG = Logger.getLogger(UserEventProducer.class);
    private static final String CHANNEL = "user-events-out";

    @Inject
    KafkaEventProducer kafkaEventProducer;

    @Inject
    TraceContextPropagator traceContextPropagator;

    public void publishUserCreated(UserEntity user, String tenantId) {
        UserEventPayload payload = new UserEventPayload(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                "CREATED"
        );

        UserEvent event = UserEvent.of(EventType.USER_CREATED, payload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        LOG.infof("Publishing USER_CREATED event for user %s in tenant %s", user.getId(), tenantId);
        kafkaEventProducer.publish(CHANNEL, event);
    }

    public void publishUserUpdated(UserEntity user, String tenantId) {
        UserEventPayload payload = new UserEventPayload(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                "UPDATED"
        );

        UserEvent event = UserEvent.of(EventType.USER_UPDATED, payload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        LOG.infof("Publishing USER_UPDATED event for user %s in tenant %s", user.getId(), tenantId);
        kafkaEventProducer.publish(CHANNEL, event);
    }

    public void publishUserDeleted(UUID userId, String tenantId) {
        UserEventPayload payload = new UserEventPayload(
                userId.toString(),
                null,
                null,
                null,
                "DELETED"
        );

        UserEvent event = UserEvent.of(EventType.USER_DELETED, payload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        LOG.infof("Publishing USER_DELETED event for user %s in tenant %s", userId, tenantId);
        kafkaEventProducer.publish(CHANNEL, event);
    }
}
