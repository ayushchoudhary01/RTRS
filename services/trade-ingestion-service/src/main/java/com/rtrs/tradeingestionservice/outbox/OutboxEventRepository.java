package com.rtrs.tradeingestionservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // FOR UPDATE SKIP LOCKED — allows concurrent outbox publishers without duplicate processing. Each instance locks its batch, others skip locked rows.
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE processed = FALSE
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findUnprocessedBatch(@Param("batchSize") int batchSize);
}