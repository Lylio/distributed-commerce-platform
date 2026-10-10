package com.lylecommerce.inventory.messaging;
import com.lylecommerce.inventory.application.ReserveInventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class OrderCreatedEventListener {
    private final ReserveInventoryService service;
    public OrderCreatedEventListener(ReserveInventoryService service) { this.service = service; }
    @KafkaListener(topics = "order-created", groupId = "inventory-service")
    public void handleOrderCreated(OrderCreatedEvent event) { service.reserve(event); }
}
