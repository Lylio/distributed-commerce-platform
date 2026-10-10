package com.lylecommerce.order.monitoring;

import java.time.Instant;

public record MonitorEvent(long sequence, String key, String orderId, String type, String source,
        String destination, String stage, String status, Instant occurredAt, Instant observedAt,
        String eventId, String topic, Integer partition, Long offset) {}
