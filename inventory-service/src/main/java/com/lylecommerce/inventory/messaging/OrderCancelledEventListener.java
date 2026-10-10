package com.lylecommerce.inventory.messaging;

import com.lylecommerce.inventory.application.ReleaseInventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCancelledEventListener {
    private final ReleaseInventoryService service;
    public OrderCancelledEventListener(ReleaseInventoryService service) { this.service = service; }

    @KafkaListener(topics = "order-cancelled", groupId = "inventory-service-cancellation",
            containerFactory = "cancellationKafkaListenerContainerFactory")
    public void onCancelled(OrderCancelledEvent event) {
        boolean released = service.release(event.orderId());
        System.out.println("Cancellation " + event.orderId() + (released ? ": stock released" : ": already released or unknown"));
    }
}
