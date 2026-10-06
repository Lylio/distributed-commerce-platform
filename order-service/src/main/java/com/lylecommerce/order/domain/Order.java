package com.lylecommerce.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Order {

    private final UUID id;
    private final UUID customerId;
    private final List<OrderItem> items;
    private final Instant createdAt;

    private OrderStatus status;
    private Instant updatedAt;

    public Order(
            UUID id,
            UUID customerId,
            List<OrderItem> items,
            Instant createdAt) {

        this.id = Objects.requireNonNull(
                id,
                "Order ID must not be null"
        );

        this.customerId = Objects.requireNonNull(
                customerId,
                "Customer ID must not be null"
        );

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "Order must contain at least one item"
            );
        }

        this.items = List.copyOf(items);

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Created time must not be null"
        );

        this.status = OrderStatus.PENDING;
        this.updatedAt = createdAt;
    }

    public static Order create(
            UUID customerId,
            List<OrderItem> items) {

        Instant now = Instant.now();

        return new Order(
                UUID.randomUUID(),
                customerId,
                items,
                now
        );
    }

    public BigDecimal calculateTotal() {
        return items.stream()
                .map(OrderItem::calculateTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void reserveInventory() {
        requireStatus(OrderStatus.PENDING);

        status = OrderStatus.INVENTORY_RESERVED;
        updatedAt = Instant.now();
    }

    public void startPayment() {
        requireStatus(OrderStatus.INVENTORY_RESERVED);

        status = OrderStatus.PAYMENT_PENDING;
        updatedAt = Instant.now();
    }

    public void confirm() {
        requireStatus(OrderStatus.PAYMENT_PENDING);

        status = OrderStatus.CONFIRMED;
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "A confirmed order cannot be cancelled"
            );
        }

        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Order is already cancelled"
            );
        }

        status = OrderStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    private void requireStatus(OrderStatus requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException(
                    "Order must be " + requiredStatus
                            + " but was " + status
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
