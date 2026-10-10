package com.lylecommerce.order.application;

import com.lylecommerce.order.domain.*;
import com.lylecommerce.order.infrastructure.messaging.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderWorkflowService {
    private final OrderRepository orders;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    public OrderWorkflowService(OrderRepository orders, JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.orders = orders; this.jdbc = jdbc; this.events = events;
    }

    @Transactional
    public void inventoryReserved(UUID orderId) {
        Order order = lock(orderId);
        // Late reservation messages must never regress a terminal or payment state.
        if (order.getStatus() != OrderStatus.PENDING) return;
        order.reserveInventory();
        var results = jdbc.queryForList("SELECT status FROM order_payment_results WHERE order_id = ?", String.class, orderId);
        if (!results.isEmpty()) applyPayment(order, results.getFirst());
        orders.save(order);
    }

    @Transactional
    public void paymentResult(PaymentResultEvent event) {
        if (!"SUCCEEDED".equals(event.status()) && !"FAILED".equals(event.status()))
            throw new IllegalArgumentException("Unknown payment status: " + event.status());
        Order order = lock(event.orderId());
        var previous = jdbc.queryForList("SELECT status FROM order_payment_results WHERE order_id = ?", String.class, event.orderId());
        if (!previous.isEmpty() && !previous.getFirst().equals(event.status()))
            throw new IllegalStateException("Conflicting payment result for " + event.orderId());
        if (order.getStatus() == OrderStatus.CONFIRMED && !"SUCCEEDED".equals(event.status()))
            throw new IllegalStateException("Cannot fail a confirmed order");
        if (order.getStatus() == OrderStatus.CANCELLED && previous.isEmpty())
            throw new IllegalStateException("Unexpected payment for an inventory-rejected order");
        if (previous.isEmpty()) jdbc.update("INSERT INTO order_payment_results(order_id, event_id, status) VALUES (?, ?, ?)",
                event.orderId(), event.eventId(), event.status());
        // Different topics/groups can deliver payment before the reservation notification.
        // Persist it now and apply it when inventoryReserved arrives.
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.CANCELLED) return;
        applyPayment(order, event.status());
        orders.save(order);
    }

    @Transactional
    public void inventoryRejected(InventoryRejectedEvent event) {
        if (event.reason() == null || event.reason().isBlank())
            throw new IllegalArgumentException("Inventory rejection requires a reason");
        Order order = lock(event.orderId());
        if (order.getStatus() == OrderStatus.CANCELLED) {
            String reason = jdbc.queryForObject("SELECT cancellation_reason FROM orders WHERE id = ?", String.class, order.getId());
            if (!event.reason().equals(reason)) throw new IllegalStateException("Conflicting inventory rejection");
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING)
            throw new IllegalStateException("Inventory rejection conflicts with " + order.getStatus());
        if (!jdbc.queryForList("SELECT status FROM order_payment_results WHERE order_id = ?", String.class, order.getId()).isEmpty())
            throw new IllegalStateException("Inventory rejection conflicts with a payment result");
        order.cancel();
        orders.save(order);
        jdbc.update("UPDATE orders SET cancellation_reason = ? WHERE id = ?", event.reason(), order.getId());
        // No stock was reserved and no payment was requested: no compensation event is needed.
    }

    private Order lock(UUID id) {
        return orders.findByIdForUpdate(id).orElseThrow(() -> new IllegalStateException("Unknown order: " + id));
    }
    private void applyPayment(Order order, String result) {
        if (order.getStatus() == OrderStatus.INVENTORY_RESERVED) order.startPayment();
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING)
            throw new IllegalStateException("Unexpected order status: " + order.getStatus());
        if ("SUCCEEDED".equals(result)) order.confirm();
        else {
            order.cancel();
            jdbc.update("UPDATE orders SET cancellation_reason = 'PAYMENT_FAILED' WHERE id = ?", order.getId());
            events.publishEvent(new OrderCancelledEvent(UUID.randomUUID(), order.getId(), Instant.now()));
        }
    }
}
