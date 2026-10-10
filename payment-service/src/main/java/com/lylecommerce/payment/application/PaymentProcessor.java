package com.lylecommerce.payment.application;

import com.lylecommerce.payment.infrastructure.outbox.OutboxStore;
import com.lylecommerce.payment.messaging.PaymentResultEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentProcessor {
    private final JdbcTemplate jdbc;
    private final OutboxStore outbox;
    private final String outcome;
    private final boolean forceFailure;

    public PaymentProcessor(JdbcTemplate jdbc, OutboxStore outbox, String outcome) {
        this(jdbc, outbox, outcome, false);
    }

    @Autowired
    public PaymentProcessor(JdbcTemplate jdbc, OutboxStore outbox,
                            @Value("${payment.simulation.outcome:AUTO}") String outcome,
                            @Value("${payment.simulation.force-failure:false}") boolean forceFailure) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.outcome = outcome;
        this.forceFailure = forceFailure;
    }

    /** Demo only: no payment gateway or card data. */
    @Transactional
    public String process(UUID orderId) {
        String simulatedStatus = forceFailure ? "FAILED" : switch (outcome) {
            case "SUCCEEDED", "FAILED" -> outcome;
            case "AUTO" -> Math.floorMod(orderId.hashCode(), 10) == 0 ? "FAILED" : "SUCCEEDED";
            default -> throw new IllegalArgumentException("Unknown payment simulation outcome: " + outcome);
        };
        int inserted = jdbc.update(
                "INSERT INTO payments(order_id, status) VALUES (?, ?) ON CONFLICT (order_id) DO NOTHING",
                orderId, simulatedStatus);
        String status = jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
        if (inserted == 1) {
            var event = new PaymentResultEvent(UUID.randomUUID(), orderId, status, Instant.now());
            outbox.append(event.eventId(), orderId, "payment-result", event);
        }
        return status;
    }
}
