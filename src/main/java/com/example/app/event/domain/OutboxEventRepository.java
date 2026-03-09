package com.example.app.event.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OutboxEventRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

    public List<OutboxEvent> findPending() {
        return list("status = ?1 order by createdAt asc", OutboxEvent.OutboxStatus.PENDING);
    }

    public List<OutboxEvent> findPending(int limit) {
        return find("status = ?1 order by createdAt asc", OutboxEvent.OutboxStatus.PENDING)
                .page(0, limit)
                .list();
    }

    @Transactional
    public void markProcessed(UUID id) {
        update("status = ?1, processedAt = ?2 where id = ?3",
                OutboxEvent.OutboxStatus.PROCESSED, Instant.now(), id);
    }

    @Transactional
    public void markFailed(UUID id) {
        update("status = ?1 where id = ?2", OutboxEvent.OutboxStatus.FAILED, id);
    }

    public long countPending() {
        return count("status", OutboxEvent.OutboxStatus.PENDING);
    }
}
