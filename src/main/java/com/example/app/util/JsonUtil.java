package com.example.app.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.jboss.logging.Logger;

import java.util.Optional;

public final class JsonUtil {

    private static final Logger LOG = Logger.getLogger(JsonUtil.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonUtil() {
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialize object of type %s to JSON", obj.getClass().getSimpleName());
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to deserialize JSON to %s", clazz.getSimpleName());
            throw new IllegalStateException("JSON deserialization failed", e);
        }
    }

    public static <T> Optional<T> tryFromJson(String json, Class<T> clazz) {
        try {
            return Optional.of(MAPPER.readValue(json, clazz));
        } catch (JsonProcessingException e) {
            LOG.warnf("Could not deserialize JSON to %s: %s", clazz.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
