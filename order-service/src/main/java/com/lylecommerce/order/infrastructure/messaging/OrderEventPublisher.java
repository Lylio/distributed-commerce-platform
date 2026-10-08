package com.lylecommerce.order.infrastructure.messaging;

public interface OrderEventPublisher {
    void publishOrderCreated(OrderCreatedEvent event);
}
