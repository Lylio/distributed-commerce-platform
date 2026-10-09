package com.lylecommerce.inventory.messaging;

import com.lylecommerce.inventory.application.ReserveInventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OrderCreatedEventListener {

    private static final String INVENTORY_RESERVED_TOPIC =
            "inventory-reserved";

    private final ReserveInventoryService reserveInventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderCreatedEventListener(
            ReserveInventoryService reserveInventoryService,
            KafkaTemplate<String, Object> kafkaTemplate) {

        this.reserveInventoryService = reserveInventoryService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "order-created",
            groupId = "inventory-service"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {

        boolean newlyReserved = reserveInventoryService.reserve(event);
        if (!newlyReserved) {
            return;
        }

        InventoryReservedEvent reservedEvent =
                new InventoryReservedEvent(
                        UUID.randomUUID(),
                        event.orderId(),
                        Instant.now()
                );

        kafkaTemplate.send(
                INVENTORY_RESERVED_TOPIC,
                event.orderId().toString(),
                reservedEvent
        ).join();

        System.out.println(
                "Published InventoryReservedEvent for order: "
                        + event.orderId()
        );
    }
}