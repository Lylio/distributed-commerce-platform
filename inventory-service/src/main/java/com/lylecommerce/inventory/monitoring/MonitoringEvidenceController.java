package com.lylecommerce.inventory.monitoring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.List;

/** Read-only committed evidence. No business listener or transaction depends on monitoring. */
@RestController
public class MonitoringEvidenceController {
    private final JdbcTemplate jdbc;
    public MonitoringEvidenceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Evidence(String key, String orderId, String type, String source, String destination,
                           String stage, String status, String occurredAt, String eventId) {}
    @GetMapping("/internal/monitoring/evidence")
    public List<Evidence> evidence() {
        var events = new ArrayList<Evidence>();
        jdbc.query("SELECT event_id, aggregate_id, topic, payload->>'status', created_at, published_at FROM outbox_events ORDER BY created_at DESC LIMIT 2000", rs -> {
            String id = rs.getString(1);
            String type = switch (rs.getString(3)) {
                case "order-created" -> "OrderCreatedEvent";
                case "inventory-reserved" -> "InventoryReservedEvent";
                case "inventory-rejected" -> "InventoryRejectedEvent";
                case "payment-result" -> "PaymentResultEvent";
                case "order-cancelled" -> "OrderCancelledEvent";
                default -> rs.getString(3);
            };
            String status = rs.getString(4) == null ? "RECORDED" : rs.getString(4);
            events.add(new Evidence("outbox:" + id, rs.getString(2), type, "inventory-service", "", "OUTBOX_INSERTED", status, rs.getTimestamp(5).toInstant().toString(), id));
            if (rs.getTimestamp(6) != null) events.add(new Evidence("published:" + id, rs.getString(2), type, "inventory-service", "kafka", "PUBLISHED", "PUBLISHED", rs.getTimestamp(6).toInstant().toString(), id));
        });
        jdbc.query("SELECT order_id, outcome, reserved_at, released_at FROM inventory_reservations ORDER BY GREATEST(reserved_at, released_at) DESC LIMIT 2000", rs -> {
            String id = rs.getString(1);
            String outcome = rs.getString(2);
            events.add(new Evidence("reservation:" + id, id, "OrderCreatedEvent", "kafka", "inventory-service", "PROCESSED", outcome, rs.getTimestamp(3).toInstant().toString(), null));
            if (rs.getTimestamp(4) != null) events.add(new Evidence("release:" + id, id, "InventoryReleased", "kafka", "inventory-service", "COMPENSATION_COMMITTED", "RELEASED", rs.getTimestamp(4).toInstant().toString(), null));
        });
        return events;
    }
}
