package com.lylecommerce.order.infrastructure.messaging;
import com.lylecommerce.order.infrastructure.outbox.OutboxStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
@Component
public class OrderCancellationPublisher {
    private final OutboxStore outbox;
    public OrderCancellationPublisher(OutboxStore outbox) { this.outbox = outbox; }
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCancelled(OrderCancelledEvent event) {
        outbox.append(event.eventId(), event.orderId(), "order-cancelled", event);
    }
}
