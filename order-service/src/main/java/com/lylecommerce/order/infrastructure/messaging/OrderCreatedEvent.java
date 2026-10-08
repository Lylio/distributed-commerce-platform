package com.lylecommerce.order.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        BigDecimal total,
        Instant occurredAt,
        List<Item> items) {

    public record Item(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice) {
    }
}
