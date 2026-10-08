package com.lylecommerce.inventory.application;

import com.lylecommerce.inventory.domain.InventoryItem;
import com.lylecommerce.inventory.domain.InventoryRepository;
import com.lylecommerce.inventory.messaging.OrderCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReserveInventoryService {

    private final InventoryRepository inventoryRepository;

    public ReserveInventoryService(
            InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public void reserve(OrderCreatedEvent event) {

        for (OrderCreatedEvent.Item orderItem : event.items()) {

            InventoryItem inventory = inventoryRepository
                    .findByProductId(orderItem.productId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Product not found: " + orderItem.productId()
                    ));

            inventory.reserve(orderItem.quantity());

            inventoryRepository.save(inventory);
        }

        System.out.println(
                "Inventory reserved successfully for order: "
                        + event.orderId()
        );
    }
}
