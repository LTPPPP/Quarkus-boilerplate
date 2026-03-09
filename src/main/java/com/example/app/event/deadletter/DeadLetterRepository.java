package com.example.app.event.deadletter;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DeadLetterRepository implements PanacheRepositoryBase<DeadLetterEvent, UUID> {

    public Optional<DeadLetterEvent> findByEventId(String eventId) {
        return find("eventId", eventId).firstResultOptional();
    }

    public List<DeadLetterEvent> findUnresolved() {
        return list("resolved = false order by createdAt desc");
    }

    public List<DeadLetterEvent> findByTopic(String topic) {
        return list("topic", topic);
    }

    public long countUnresolved() {
        return count("resolved", false);
    }
}
