package com.lylecommerce.order.infrastructure.messaging;
import com.lylecommerce.order.application.OrderWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class InventoryReservedEventListener {
    private final OrderWorkflowService workflow;
    public InventoryReservedEventListener(OrderWorkflowService workflow) { this.workflow = workflow; }
    @KafkaListener(topics = "inventory-reserved", groupId = "order-service")
    public void handleInventoryReserved(InventoryReservedEvent event) { workflow.inventoryReserved(event.orderId()); }
}
