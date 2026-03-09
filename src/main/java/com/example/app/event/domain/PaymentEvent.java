package com.example.app.event.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEvent extends BaseEvent<PaymentEventPayload> {

    public PaymentEvent() {
        super();
    }

    public PaymentEvent(String eventType, PaymentEventPayload payload, String tenantId) {
        super(eventType, payload, tenantId);
    }

    public PaymentEvent(String eventType, PaymentEventPayload payload, String tenantId, String traceId) {
        super(eventType, payload, tenantId, traceId);
    }

    public static PaymentEvent of(EventType eventType, PaymentEventPayload payload, String tenantId) {
        return new PaymentEvent(eventType.getValue(), payload, tenantId);
    }

    public static PaymentEvent of(EventType eventType, PaymentEventPayload payload, String tenantId, String traceId) {
        return new PaymentEvent(eventType.getValue(), payload, tenantId, traceId);
    }
}
