package com.lylecommerce.order.infrastructure.messaging;
import com.lylecommerce.order.application.OrderWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class InventoryRejectedEventListener {
    private final OrderWorkflowService workflow;
    public InventoryRejectedEventListener(OrderWorkflowService workflow) { this.workflow = workflow; }
    @KafkaListener(topics = "inventory-rejected", groupId = "order-service-rejection", containerFactory = "rejectionKafkaListenerContainerFactory")
    public void onRejected(InventoryRejectedEvent event) { workflow.inventoryRejected(event); }
}
