package com.lylecommerce.order.infrastructure.messaging;
import java.time.Instant;
import java.util.UUID;
public record OrderCancelledEvent(UUID eventId, UUID orderId, Instant occurredAt) {}
