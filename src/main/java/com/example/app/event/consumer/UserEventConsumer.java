package com.example.app.event.consumer;

import com.example.app.event.domain.EventType;
import com.example.app.event.domain.UserEvent;
import com.example.app.event.domain.UserEventPayload;
import com.example.app.event.handler.UserEventHandler;
import com.example.app.tenant.context.TenantContext;
import com.example.app.util.JsonUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class UserEventConsumer {

    private static final Logger LOG = Logger.getLogger(UserEventConsumer.class);

    @Inject
    UserEventHandler userEventHandler;

    @Incoming("user-events-in")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<Void> processUserEvent(Message<String> message) {
        String json = message.getPayload();
        UserEvent event = null;
        try {
            event = JsonUtil.fromJson(json, UserEvent.class);
            LOG.infof("Received user event [%s], eventId=%s, tenant=%s",
                    event.getEventType(), event.getEventId(), event.getTenantId());

            if (event.getTenantId() != null) {
                TenantContext.setCurrentTenant(event.getTenantId());
            }

            UserEventPayload payload = event.getPayload();
            String eventTypeValue = event.getEventType();

            if (EventType.USER_CREATED.getValue().equals(eventTypeValue)) {
                userEventHandler.onUserCreated(payload, event.getTenantId());
            } else if (EventType.USER_UPDATED.getValue().equals(eventTypeValue)) {
                userEventHandler.onUserUpdated(payload, event.getTenantId());
            } else if (EventType.USER_DELETED.getValue().equals(eventTypeValue)) {
                userEventHandler.onUserDeleted(payload, event.getTenantId());
            } else {
                LOG.warnf("Unknown user event type: %s", eventTypeValue);
            }

            LOG.infof("Successfully processed user event [%s], eventId=%s", eventTypeValue, event.getEventId());
            return message.ack();
        } catch (Exception e) {
            String eventId = event != null ? event.getEventId() : "unknown";
            LOG.errorf(e, "Failed to process user event, eventId=%s", eventId);
            return message.nack(e);
        } finally {
            TenantContext.clear();
        }
    }
}
