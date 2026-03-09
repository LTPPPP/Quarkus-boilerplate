package com.example.app.event.producer;

import com.example.app.domain.OrderEntity;
import com.example.app.domain.OrderItemEntity;
import com.example.app.event.domain.EventType;
import com.example.app.event.domain.OrderEvent;
import com.example.app.event.domain.OrderEventPayload;
import com.example.app.event.domain.OrderEventPayload.OrderItemPayload;
import com.example.app.util.TraceContextPropagator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class OrderEventProducer {

    private static final Logger LOG = Logger.getLogger(OrderEventProducer.class);
    private static final String CHANNEL = "order-events-out";

    @Inject
    KafkaEventProducer kafkaEventProducer;

    @Inject
    TraceContextPropagator traceContextPropagator;

    public void publishOrderPlaced(OrderEntity order, String tenantId) {
        OrderEventPayload payload = buildPayload(order);
        OrderEvent event = OrderEvent.of(EventType.ORDER_PLACED, payload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        LOG.infof("Publishing ORDER_PLACED event for order %s in tenant %s", order.getId(), tenantId);
        kafkaEventProducer.publish(CHANNEL, event);
    }

    public void publishOrderStatusChanged(OrderEntity order, String previousStatus, String tenantId) {
        OrderEventPayload payload = buildPayload(order);
        payload.setPreviousStatus(previousStatus);

        EventType eventType = switch (order.getStatus()) {
            case CANCELLED -> EventType.ORDER_CANCELLED;
            case COMPLETED -> EventType.ORDER_COMPLETED;
            default -> EventType.ORDER_PLACED;
        };

        OrderEvent event = OrderEvent.of(eventType, payload, tenantId,
                traceContextPropagator.getCurrentTraceId());

        LOG.infof("Publishing %s event for order %s in tenant %s", eventType, order.getId(), tenantId);
        kafkaEventProducer.publish(CHANNEL, event);
    }

    private OrderEventPayload buildPayload(OrderEntity order) {
        List<OrderItemPayload> itemPayloads = order.getItems().stream()
                .map(this::toItemPayload)
                .collect(Collectors.toList());

        return new OrderEventPayload(
                order.getId().toString(),
                order.getUserId().toString(),
                order.getTotalAmount(),
                order.getStatus().name(),
                itemPayloads
        );
    }

    private OrderItemPayload toItemPayload(OrderItemEntity item) {
        return new OrderItemPayload(
                item.getProductId(),
                item.getName(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }
}
