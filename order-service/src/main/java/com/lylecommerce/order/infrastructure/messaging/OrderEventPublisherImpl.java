package com.lylecommerce.order.infrastructure.messaging;
import com.lylecommerce.order.infrastructure.outbox.OutboxStore;
import org.springframework.stereotype.Component;
@Component
public class OrderEventPublisherImpl implements OrderEventPublisher {
    private final OutboxStore outbox;
    public OrderEventPublisherImpl(OutboxStore outbox) { this.outbox = outbox; }
    public void publishOrderCreated(OrderCreatedEvent event) {
        outbox.append(event.eventId(), event.orderId(), "order-created", event);
    }
}
