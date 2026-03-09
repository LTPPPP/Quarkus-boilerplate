package com.example.app.event.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseEvent<T> {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private String version;
    private String traceId;
    private String tenantId;
    private T payload;

    protected BaseEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.version = "1.0";
    }

    protected BaseEvent(String eventType, T payload, String tenantId) {
        this();
        this.eventType = eventType;
        this.payload = payload;
        this.tenantId = tenantId;
    }

    protected BaseEvent(String eventType, T payload, String tenantId, String traceId) {
        this(eventType, payload, tenantId);
        this.traceId = traceId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", occurredAt=" + occurredAt +
                ", tenantId='" + tenantId + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
