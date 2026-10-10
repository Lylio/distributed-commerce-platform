package com.lylecommerce.inventory.application;

import com.lylecommerce.inventory.domain.InventoryItem;
import com.lylecommerce.inventory.domain.InventoryRepository;
import com.lylecommerce.inventory.domain.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ReleaseInventoryService {
    private final InventoryRepository inventory;
    private final InventoryReservationRepository reservations;
    public ReleaseInventoryService(InventoryRepository inventory, InventoryReservationRepository reservations) {
        this.inventory = inventory;
        this.reservations = reservations;
    }

    @Transactional
    public boolean release(UUID orderId) {
        var items = reservations.lockActiveItems(orderId);
        if (items == null) return false;
        if (items.isEmpty()) throw new IllegalStateException("No recorded items for reservation " + orderId
                + "; legacy reservations must be reconciled manually");
        for (var item : items) {
            InventoryItem stock = inventory.findByProductId(item.productId())
                    .orElseThrow(() -> new IllegalStateException("Product missing: " + item.productId()));
            stock.release(item.quantity());
            inventory.save(stock);
        }
        reservations.markReleased(orderId);
        return true;
    }
}
