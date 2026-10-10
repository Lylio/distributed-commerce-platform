package com.lylecommerce.order.monitoring;

import com.lylecommerce.order.OrderServiceApplication;
import com.lylecommerce.order.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = OrderServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonitoringStoreIntegrationTest extends PostgresIntegrationTest {
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired MonitoringStore store;
    @Autowired JdbcTemplate jdbc;
    @Test void persistsDistinctStagesDeduplicatesAndFiltersRetainedOrderHistory() {
        String order = UUID.randomUUID().toString(); String other = UUID.randomUUID().toString(); String prefix = UUID.randomUUID().toString();
        Instant now = Instant.now();
        store.save(prefix + ":outbox", order, "OrderCreatedEvent", "order-service", "", "OUTBOX_INSERTED", "RECORDED", now, "event-a", null, null, null);
        store.save(prefix + ":kafka", order, "OrderCreatedEvent", "order-service", "kafka", "KAFKA_OBSERVED", "OBSERVED", now, "event-a", "order-created", 0, 99L);
        store.save(prefix + ":kafka", order, "OrderCreatedEvent", "order-service", "kafka", "KAFKA_OBSERVED", "OBSERVED", now, "event-a", "order-created", 0, 99L);
        store.save(prefix + ":other", other, "PaymentResultEvent", "payment-service", "kafka", "KAFKA_OBSERVED", "FAILED", now, "event-b", "payment-result", 0, 100L);
        var events = store.read(0, order, "", "", 100, true);
        assertEquals(2, events.size());
        var client = org.springframework.web.client.RestClient.create("http://localhost:" + port);
        MonitorEvent[] api = client.get().uri("/monitoring/events?orderId=" + order + "&limit=5000").retrieve().body(MonitorEvent[].class);
        assertNotNull(api);
        assertEquals(2, api.length);
        assertEquals(1, client.get().uri("/monitoring/events?orderId=" + order + "&type=OrderCreatedEvent&service=kafka").retrieve().body(MonitorEvent[].class).length);
        assertEquals("OUTBOX_INSERTED", events.getFirst().stage());
        assertEquals(99L, events.getLast().offset());
        assertEquals(1, store.read(0, order, "OrderCreatedEvent", "kafka", 100, true).size());
        assertEquals(0, store.read(0, order, "PaymentResultEvent", "", 100, true).size());
        assertEquals(1, store.read(events.getFirst().sequence(), order, "", "", 100, false).size());
        // Retention removes old telemetry without touching business data.
        jdbc.update("UPDATE monitoring_events SET occurred_at = CURRENT_TIMESTAMP - INTERVAL '8 days' WHERE evidence_key = ?", prefix + ":outbox");
        store.prune();
        assertEquals(1, store.read(0, order, "", "", 100, true).size());
    }
}
