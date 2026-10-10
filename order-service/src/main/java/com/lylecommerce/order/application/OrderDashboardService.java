package com.lylecommerce.order.application;
import com.lylecommerce.order.api.OrderResponse;
import com.lylecommerce.order.domain.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service
public class OrderDashboardService {
    public record Transition(String status, Instant occurredAt) {}
    public record Detail(OrderResponse order, String paymentStatus, String cancellationReason,
                         String currency, List<Transition> transitions) {}
    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    public OrderDashboardService(JdbcTemplate jdbc, OrderRepository orders) { this.jdbc = jdbc; this.orders = orders; }
    @Transactional(readOnly = true)
    public List<Detail> list(UUID customerId, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("Limit must be between 1 and 200");
        var ids = customerId == null
                ? jdbc.queryForList("SELECT id FROM orders ORDER BY created_at DESC, id LIMIT ?", UUID.class, limit)
                : jdbc.queryForList("SELECT id FROM orders WHERE customer_id = ? ORDER BY created_at DESC, id LIMIT ?", UUID.class, customerId, limit);
        return ids.stream().map(this::detail).toList();
    }
    @Transactional(readOnly = true)
    public Detail detail(UUID id) {
        var order = orders.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Order not found"));
        String reason = jdbc.queryForObject("SELECT cancellation_reason FROM orders WHERE id = ?", String.class, id);
        var payment = jdbc.queryForList("SELECT status FROM order_payment_results WHERE order_id = ?", String.class, id);
        var history = jdbc.query("SELECT status, occurred_at FROM order_status_history WHERE order_id = ? ORDER BY sequence",
                (rs, row) -> new Transition(rs.getString(1), rs.getTimestamp(2).toInstant()), id);
        return new Detail(OrderResponse.from(order), payment.isEmpty() ? "NOT_REQUESTED" : payment.getFirst(), reason, "GBP", history);
    }
}
