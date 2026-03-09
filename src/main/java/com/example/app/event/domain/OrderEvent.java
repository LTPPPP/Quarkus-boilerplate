package com.example.app.event.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent extends BaseEvent<OrderEventPayload> {

    public OrderEvent() {
        super();
    }

    public OrderEvent(String eventType, OrderEventPayload payload, String tenantId) {
        super(eventType, payload, tenantId);
    }

    public OrderEvent(String eventType, OrderEventPayload payload, String tenantId, String traceId) {
        super(eventType, payload, tenantId, traceId);
    }

    public static OrderEvent of(EventType eventType, OrderEventPayload payload, String tenantId) {
        return new OrderEvent(eventType.getValue(), payload, tenantId);
    }

    public static OrderEvent of(EventType eventType, OrderEventPayload payload, String tenantId, String traceId) {
        return new OrderEvent(eventType.getValue(), payload, tenantId, traceId);
    }
}
