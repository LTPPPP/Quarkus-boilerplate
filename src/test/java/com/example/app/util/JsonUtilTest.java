package com.example.app.util;

import com.example.app.event.domain.EventType;
import com.example.app.event.domain.UserEvent;
import com.example.app.event.domain.UserEventPayload;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonUtilTest {

    @Test
    void testSerializeAndDeserializeMap() {
        Map<String, Object> data = Map.of("key", "value", "number", 42);
        String json = JsonUtil.toJson(data);
        assertNotNull(json);
        assertTrue(json.contains("key"));
        assertTrue(json.contains("value"));

        Map<?, ?> deserialized = JsonUtil.fromJson(json, Map.class);
        assertEquals("value", deserialized.get("key"));
        assertEquals(42, deserialized.get("number"));
    }

    @Test
    void testSerializeUserEvent() {
        UserEvent event = UserEvent.of(
                EventType.USER_CREATED,
                new UserEventPayload("u1", "a@b.com", "Alice", "USER", "CREATED"),
                "tenant-1"
        );

        String json = JsonUtil.toJson(event);
        assertNotNull(json);
        assertTrue(json.contains("u1"));
        assertTrue(json.contains("a@b.com"));
        assertTrue(json.contains("user.created"));

        UserEvent deserialized = JsonUtil.fromJson(json, UserEvent.class);
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals("a@b.com", deserialized.getPayload().getEmail());
    }

    @Test
    void testDeserializeInvalidJsonThrows() {
        assertThrows(Exception.class,
                () -> JsonUtil.fromJson("{invalid json", Map.class));
    }

    @Test
    void testSerializeNull() {
        String json = JsonUtil.toJson(null);
        assertEquals("null", json);
    }

    @Test
    void testDeserializeEmptyObject() {
        Map<?, ?> result = JsonUtil.fromJson("{}", Map.class);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
