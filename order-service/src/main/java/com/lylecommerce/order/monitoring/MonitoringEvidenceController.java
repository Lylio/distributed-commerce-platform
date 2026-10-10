package com.lylecommerce.order.monitoring;

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
            events.add(new Evidence("outbox:" + id, rs.getString(2), type, "order-service", "", "OUTBOX_INSERTED", status, rs.getTimestamp(5).toInstant().toString(), id));
            if (rs.getTimestamp(6) != null) events.add(new Evidence("published:" + id, rs.getString(2), type, "order-service", "kafka", "PUBLISHED", "PUBLISHED", rs.getTimestamp(6).toInstant().toString(), id));
        });
        jdbc.query("SELECT sequence, order_id, status, occurred_at FROM order_status_history ORDER BY sequence DESC LIMIT 2000", rs -> {
            String status = rs.getString(3);
            String type = switch (status) { case "PENDING" -> "OrderCreated"; case "CONFIRMED" -> "OrderConfirmed"; case "CANCELLED" -> "OrderCancelled"; default -> "OrderState:" + status; };
            events.add(new Evidence("order-state:" + rs.getLong(1), rs.getString(2), type, "order-service",
                    "PENDING".equals(status) ? "" : "order-service", "STATE_COMMITTED", status, rs.getTimestamp(4).toInstant().toString(), null));
        });
        jdbc.query("SELECT event_id, order_id, status, received_at FROM order_payment_results ORDER BY received_at DESC LIMIT 2000", rs -> {
            events.add(new Evidence("payment-received:" + rs.getString(1), rs.getString(2), "PaymentResultEvent", "kafka", "order-service", "PROCESSED", rs.getString(3), rs.getTimestamp(4).toInstant().toString(), rs.getString(1)));
        });
        return events;
    }
}
