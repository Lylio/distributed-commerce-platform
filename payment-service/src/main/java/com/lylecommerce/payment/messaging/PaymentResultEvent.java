package com.lylecommerce.payment.messaging;
import java.time.Instant;
import java.util.UUID;
public record PaymentResultEvent(UUID eventId, UUID orderId, String status, Instant occurredAt) {}
