package com.lylecommerce.inventory.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** At-least-once relay: an acknowledgement followed by a crash can publish the same event again. */
@Component
@EnableScheduling
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final boolean schedulingEnabled;
    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, Object> kafka, ObjectMapper mapper,
                           PlatformTransactionManager manager,
                           @Value("${outbox.scheduling-enabled:true}") boolean schedulingEnabled) {
        this.jdbc = jdbc; this.kafka = kafka; this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager); this.schedulingEnabled = schedulingEnabled;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    public void poll() {
        if (!schedulingEnabled) return;
        try { publishPending(); }
        catch (RuntimeException failure) { log.warn("Outbox publication failed; persisted event will be retried", failure); }
    }

    public int publishPending() {
        int published = 0;
        // Each acknowledgement commits independently; concurrent relays skip rows locked by another worker.
        for (int i = 0; i < 50; i++) {
            Boolean sent = transaction.execute(status -> {
                var rows = jdbc.query("SELECT event_id, aggregate_id, topic, payload::text FROM outbox_events "
                        + "WHERE published_at IS NULL ORDER BY created_at, event_id LIMIT 1 FOR UPDATE SKIP LOCKED",
                        (rs, row) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                                rs.getString(3), rs.getString(4)));
                if (rows.isEmpty()) return false;
                Pending event = rows.getFirst();
                try {
                    kafka.send(event.topic(), event.orderId().toString(), mapper.readTree(event.payload()))
                            .get(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException("Outbox interrupted", interrupted);
                } catch (Exception failure) {
                    throw new IllegalStateException("Could not publish event " + event.id(), failure);
                }
                jdbc.update("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP WHERE event_id = ?", event.id());
                return true;
            });
            if (!Boolean.TRUE.equals(sent)) break;
            published++;
        }
        return published;
    }
    private record Pending(UUID id, UUID orderId, String topic, String payload) {}
}
