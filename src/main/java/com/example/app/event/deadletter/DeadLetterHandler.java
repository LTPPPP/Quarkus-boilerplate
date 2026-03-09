package com.example.app.event.deadletter;

import com.example.app.event.domain.BaseEvent;
import com.example.app.event.domain.OrderEvent;
import com.example.app.event.domain.PaymentEvent;
import com.example.app.event.domain.UserEvent;
import com.example.app.event.producer.KafkaEventProducer;
import com.example.app.util.JsonUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
@Path("/api/v1/admin/dlq")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeadLetterHandler {

    private static final Logger LOG = Logger.getLogger(DeadLetterHandler.class);

    @Inject
    DeadLetterRepository deadLetterRepository;

    @Inject
    KafkaEventProducer kafkaEventProducer;

    @Incoming("dead-letter-queue")
    @Transactional
    public java.util.concurrent.CompletionStage<Void> consumeDeadLetter(org.eclipse.microprofile.reactive.messaging.Message<String> message) {
        String value = message.getPayload();

        try {
            LOG.errorf("Dead letter received: payload=%s", truncate(value, 500));

            DeadLetterEvent dlEvent = new DeadLetterEvent();
            dlEvent.setEventId(extractEventId(value));
            dlEvent.setTopic(extractOriginalTopic(value));
            dlEvent.setEventKey("dlq");
            dlEvent.setPayload(value);
            dlEvent.setErrorMessage("Event failed processing and was sent to dead letter queue");
            dlEvent.setRetryCount(0);
            dlEvent.setResolved(false);

            deadLetterRepository.persist(dlEvent);
            LOG.infof("Dead letter event persisted with id=%s, eventId=%s", dlEvent.getId(), dlEvent.getEventId());

            return message.ack();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to persist dead letter event");
            return message.nack(e);
        }
    }

    @GET
    public Response listDeadLetterEvents() {
        List<DeadLetterEvent> events = deadLetterRepository.findUnresolved();
        return Response.ok(events).build();
    }

    @GET
    @Path("/count")
    public Response countUnresolved() {
        long count = deadLetterRepository.countUnresolved();
        return Response.ok(Map.of("unresolvedCount", count)).build();
    }

    @POST
    @Path("/{eventId}/retry")
    @Transactional
    public Response retryEvent(@PathParam("eventId") String eventId) {
        DeadLetterEvent dlEvent = deadLetterRepository.findByEventId(eventId)
                .orElse(null);

        if (dlEvent == null) {
            dlEvent = deadLetterRepository.findByIdOptional(UUID.fromString(eventId))
                    .orElse(null);
        }

        if (dlEvent == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Dead letter event not found: " + eventId))
                    .build();
        }

        try {
            String payload = dlEvent.getPayload();
            BaseEvent<?> originalEvent = deserializeByEventType(payload);
            String channel = resolveChannelFromEventType(originalEvent.getEventType());

            kafkaEventProducer.publish(channel, originalEvent);

            dlEvent.setRetryCount(dlEvent.getRetryCount() + 1);
            dlEvent.setLastRetryAt(Instant.now());
            dlEvent.setResolved(true);
            deadLetterRepository.persist(dlEvent);

            LOG.infof("Dead letter event retried successfully, eventId=%s, channel=%s",
                    dlEvent.getEventId(), channel);

            return Response.ok(Map.of(
                    "status", "retried",
                    "eventId", dlEvent.getEventId(),
                    "retryCount", dlEvent.getRetryCount()
            )).build();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));

            dlEvent.setRetryCount(dlEvent.getRetryCount() + 1);
            dlEvent.setLastRetryAt(Instant.now());
            dlEvent.setErrorMessage(e.getMessage());
            dlEvent.setErrorStacktrace(sw.toString());
            deadLetterRepository.persist(dlEvent);

            LOG.errorf(e, "Retry failed for dead letter event, eventId=%s", dlEvent.getEventId());

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Retry failed", "message", e.getMessage()))
                    .build();
        }
    }

    private BaseEvent<?> deserializeByEventType(String payload) {
        try {
            Map<?, ?> map = JsonUtil.fromJson(payload, Map.class);
            String eventType = map.get("eventType") != null ? map.get("eventType").toString() : "";

            if (eventType.startsWith("order.")) {
                return JsonUtil.fromJson(payload, OrderEvent.class);
            } else if (eventType.startsWith("payment.")) {
                return JsonUtil.fromJson(payload, PaymentEvent.class);
            } else {
                return JsonUtil.fromJson(payload, UserEvent.class);
            }
        } catch (Exception e) {
            LOG.warnf("Could not determine event type, defaulting to UserEvent: %s", e.getMessage());
            return JsonUtil.fromJson(payload, UserEvent.class);
        }
    }

    private String resolveChannelFromEventType(String eventType) {
        if (eventType == null) return "notification-events-out";
        if (eventType.startsWith("user.")) return "user-events-out";
        if (eventType.startsWith("order.")) return "order-events-out";
        if (eventType.startsWith("payment.")) return "payment-events-out";
        return "notification-events-out";
    }

    private String extractEventId(String json) {
        try {
            Map<?, ?> map = JsonUtil.fromJson(json, Map.class);
            Object id = map.get("eventId");
            return id != null ? id.toString() : UUID.randomUUID().toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String extractOriginalTopic(String json) {
        try {
            Map<?, ?> map = JsonUtil.fromJson(json, Map.class);
            String eventType = map.get("eventType") != null ? map.get("eventType").toString() : "";
            if (eventType.startsWith("user.")) return "user-events";
            if (eventType.startsWith("order.")) return "order-events";
            if (eventType.startsWith("payment.")) return "payment-events";
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "null";
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }
}
