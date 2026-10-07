package com.lylecommerce.order.api;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderItem;
import com.lylecommerce.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items) {

    public record Item(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal total) {
    }

    public static OrderResponse from(Order order) {

        List<Item> items = order.getItems()
                .stream()
                .map(OrderResponse::fromItem)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.calculateTotal(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items
        );
    }

    private static Item fromItem(OrderItem item) {
        return new Item(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.calculateTotal()
        );
    }
}
