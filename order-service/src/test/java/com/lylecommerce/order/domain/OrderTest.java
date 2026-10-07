package com.lylecommerce.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void newOrderShouldStartPending() {
        Order order = createOrder();

        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void shouldCalculateOrderTotal() {
        OrderItem keyboard = new OrderItem(
                UUID.randomUUID(),
                "Mechanical Keyboard",
                2,
                new BigDecimal("79.99")
        );

        OrderItem mouse = new OrderItem(
                UUID.randomUUID(),
                "Gaming Mouse",
                1,
                new BigDecimal("49.99")
        );

        Order order = Order.create(
                UUID.randomUUID(),
                List.of(keyboard, mouse)
        );

        assertEquals(
                new BigDecimal("209.97"),
                order.calculateTotal()
        );
    }

    @Test
    void shouldRejectOrderWithoutItems() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(
                        UUID.randomUUID(),
                        List.of()
                )
        );
    }

    @Test
    void shouldReserveInventoryForPendingOrder() {
        Order order = createOrder();

        order.reserveInventory();

        assertEquals(
                OrderStatus.INVENTORY_RESERVED,
                order.getStatus()
        );
    }

    @Test
    void shouldProgressThroughSuccessfulOrderLifecycle() {
        Order order = createOrder();

        order.reserveInventory();
        order.startPayment();
        order.confirm();

        assertEquals(
                OrderStatus.CONFIRMED,
                order.getStatus()
        );
    }

    @Test
    void shouldNotConfirmPendingOrder() {
        Order order = createOrder();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                order::confirm
        );

        assertEquals(
                "Order must be PAYMENT_PENDING but was PENDING",
                exception.getMessage()
        );
    }

    @Test
    void shouldCancelPendingOrder() {
        Order order = createOrder();

        order.cancel();

        assertEquals(
                OrderStatus.CANCELLED,
                order.getStatus()
        );
    }

    @Test
    void shouldNotCancelConfirmedOrder() {
        Order order = createOrder();

        order.reserveInventory();
        order.startPayment();
        order.confirm();

        assertThrows(
                IllegalStateException.class,
                order::cancel
        );
    }

    @Test
    void shouldNotCancelOrderTwice() {
        Order order = createOrder();

        order.cancel();

        assertThrows(
                IllegalStateException.class,
                order::cancel
        );
    }

    private Order createOrder() {
        OrderItem item = new OrderItem(
                UUID.randomUUID(),
                "Mechanical Keyboard",
                1,
                new BigDecimal("79.99")
        );

        return Order.create(
                UUID.randomUUID(),
                List.of(item)
        );
    }

    @Test
    void shouldRehydrateExistingOrder() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Instant createdAt = Instant.parse(
                "2026-10-06T18:00:00Z"
        );

        Instant updatedAt = Instant.parse(
                "2026-10-06T18:05:00Z"
        );

        OrderItem item = new OrderItem(
                UUID.randomUUID(),
                "Mechanical Keyboard",
                1,
                new BigDecimal("79.99")
        );

        Order order = Order.rehydrate(
                orderId,
                customerId,
                List.of(item),
                OrderStatus.CONFIRMED,
                createdAt,
                updatedAt
        );

        assertEquals(orderId, order.getId());
        assertEquals(customerId, order.getCustomerId());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(createdAt, order.getCreatedAt());
        assertEquals(updatedAt, order.getUpdatedAt());
    }
}