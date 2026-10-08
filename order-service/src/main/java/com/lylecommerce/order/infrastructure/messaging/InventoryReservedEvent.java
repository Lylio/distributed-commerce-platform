package com.lylecommerce.order.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID eventId,
        UUID orderId,
        Instant occurredAt
) {
}
