package com.example.app.event.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEvent extends BaseEvent<UserEventPayload> {

    public UserEvent() {
        super();
    }

    public UserEvent(String eventType, UserEventPayload payload, String tenantId) {
        super(eventType, payload, tenantId);
    }

    public UserEvent(String eventType, UserEventPayload payload, String tenantId, String traceId) {
        super(eventType, payload, tenantId, traceId);
    }

    public static UserEvent of(EventType eventType, UserEventPayload payload, String tenantId) {
        return new UserEvent(eventType.getValue(), payload, tenantId);
    }

    public static UserEvent of(EventType eventType, UserEventPayload payload, String tenantId, String traceId) {
        return new UserEvent(eventType.getValue(), payload, tenantId, traceId);
    }
}
