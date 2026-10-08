package com.lylecommerce.inventory.messaging;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID eventId,
        UUID orderId,
        Instant occurredAt
) {
}
