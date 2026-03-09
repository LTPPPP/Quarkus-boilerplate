package com.example.app.event.domain;

public enum EventType {

    USER_CREATED("user.created"),
    USER_UPDATED("user.updated"),
    USER_DELETED("user.deleted"),

    ORDER_PLACED("order.placed"),
    ORDER_CANCELLED("order.cancelled"),
    ORDER_COMPLETED("order.completed"),

    PAYMENT_INITIATED("payment.initiated"),
    PAYMENT_SUCCESS("payment.success"),
    PAYMENT_FAILED("payment.failed"),

    NOTIFICATION_REQUESTED("notification.requested"),

    TENANT_PROVISIONED("tenant.provisioned"),
    TENANT_SUSPENDED("tenant.suspended"),
    TENANT_DELETED("tenant.deleted");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EventType fromValue(String value) {
        for (EventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + value);
    }
}
