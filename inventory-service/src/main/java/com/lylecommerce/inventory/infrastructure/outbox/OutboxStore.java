package com.lylecommerce.inventory.infrastructure.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Repository
public class OutboxStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public OutboxStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID eventId, UUID orderId, String topic, Object event) {
        jdbc.update("INSERT INTO outbox_events(event_id, aggregate_id, topic, payload) VALUES (?, ?, ?, ?::jsonb)",
                eventId, orderId, topic, mapper.writeValueAsString(event));
    }
}
