package com.lylecommerce.order.monitoring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Repository
public class MonitoringStore {
    private final JdbcTemplate jdbc;
    public MonitoringStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public synchronized void save(String key, String orderId, String type, String source, String destination,
                     String stage, String status, Instant occurredAt, String eventId,
                     String topic, Integer partition, Long offset) {
        if (occurredAt.isBefore(Instant.now().minus(java.time.Duration.ofDays(7)))) return;
        jdbc.update("INSERT INTO monitoring_events(evidence_key,order_id,event_type,source,destination,stage,status,occurred_at,event_id,topic,kafka_partition,kafka_offset) "
                + "VALUES (?,?,?,?,?,?,?,?::timestamptz,?,?,?,?) ON CONFLICT(evidence_key) DO NOTHING",
                key, UUID.fromString(orderId), type, source, destination, stage, status, occurredAt.toString(), eventId, topic, partition, offset);
    }
    public List<MonitorEvent> read(long after, String orderId, String type, String service, int limit, boolean recent) {
        var result = jdbc.query("SELECT * FROM monitoring_events WHERE sequence > ? "
                + "AND (? = '' OR order_id::text = ?) AND (? = '' OR event_type = ?) "
                + "AND (? = '' OR source = ? OR destination = ?) ORDER BY sequence " + (recent ? "DESC" : "ASC") + " LIMIT ?",
                (rs, n) -> new MonitorEvent(rs.getLong("sequence"), rs.getString("evidence_key"), rs.getString("order_id"),
                        rs.getString("event_type"), rs.getString("source"), rs.getString("destination"), rs.getString("stage"),
                        rs.getString("status"), rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("observed_at").toInstant(),
                        rs.getString("event_id"), rs.getString("topic"), (Integer) rs.getObject("kafka_partition"), (Long) rs.getObject("kafka_offset")),
                after, orderId, orderId, type, type, service, service, service, Math.clamp(limit, 1, 5000));
        if (recent) Collections.reverse(result);
        return result;
    }
    public long min() { Long n = jdbc.queryForObject("SELECT COALESCE(MIN(sequence),0) FROM monitoring_events", Long.class); return n == null ? 0 : n; }
    public long max() { Long n = jdbc.queryForObject("SELECT COALESCE(MAX(sequence),0) FROM monitoring_events", Long.class); return n == null ? 0 : n; }
    public void prune() {
        jdbc.update("DELETE FROM monitoring_events WHERE occurred_at < CURRENT_TIMESTAMP - INTERVAL '7 days' OR sequence < (SELECT sequence FROM monitoring_events ORDER BY sequence DESC OFFSET 19999 LIMIT 1)");
    }
}
