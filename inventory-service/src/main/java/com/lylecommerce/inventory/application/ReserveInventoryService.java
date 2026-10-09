package com.lylecommerce.inventory.application;

import com.lylecommerce.inventory.domain.InventoryItem;
import com.lylecommerce.inventory.domain.InventoryRepository;
import com.lylecommerce.inventory.domain.InventoryReservationRepository;
import com.lylecommerce.inventory.messaging.OrderCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReserveInventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    public ReserveInventoryService(
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository) {

        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public boolean reserve(OrderCreatedEvent event) {

        // Atomically claim this order before reserving stock.
        if (!reservationRepository.tryClaimOrder(event.orderId())) {
            System.out.println(
                    "Inventory already reserved for order: "
                            + event.orderId()
                            + " - skipping duplicate event"
            );
            return false;
        }

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
        return true;
    }
}
