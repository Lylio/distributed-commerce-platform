package com.lylecommerce.inventory.application;

import com.lylecommerce.inventory.domain.*;
import com.lylecommerce.inventory.messaging.*;
import com.lylecommerce.inventory.infrastructure.outbox.OutboxStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ReserveInventoryService {
    private final InventoryRepository inventory;
    private final InventoryReservationRepository reservations;
    private final OutboxStore outbox;
    public ReserveInventoryService(InventoryRepository inventory, InventoryReservationRepository reservations, OutboxStore outbox) {
        this.inventory = inventory; this.reservations = reservations; this.outbox = outbox;
    }

    /** Returns true for a newly recorded outcome, including rejection. */
    @Transactional
    public boolean reserve(OrderCreatedEvent event) {
        if (!reservations.tryClaimOrder(event.orderId())) return false;
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (var item : event.items()) {
            if (item.productId() == null || item.quantity() <= 0) throw new IllegalArgumentException("Invalid product/quantity");
            quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }
        if (quantities.isEmpty()) throw new IllegalArgumentException("Order has no items");
        Map<UUID, InventoryItem> locked = new LinkedHashMap<>();
        // Preflight every line under ordered locks before changing any stock.
        for (UUID productId : quantities.keySet().stream().sorted().toList()) {
            var stock = inventory.findByProductId(productId);
            if (stock.isEmpty()) return reject(event.orderId(), "PRODUCT_NOT_FOUND: " + productId);
            if (stock.get().getAvailableQuantity() < quantities.get(productId))
                return reject(event.orderId(), "INSUFFICIENT_STOCK: " + productId);
            locked.put(productId, stock.get());
        }
        for (var entry : locked.entrySet()) {
            entry.getValue().reserve(quantities.get(entry.getKey()));
            inventory.save(entry.getValue());
        }
        reservations.recordItems(event.orderId(), quantities.entrySet().stream()
                .map(e -> new InventoryReservationRepository.ReservedItem(e.getKey(), e.getValue())).toList());
        var result = new InventoryReservedEvent(UUID.randomUUID(), event.orderId(), Instant.now());
        outbox.append(result.eventId(), result.orderId(), "inventory-reserved", result);
        return true;
    }
    private boolean reject(UUID orderId, String reason) {
        reservations.markRejected(orderId, reason);
        var result = new InventoryRejectedEvent(UUID.randomUUID(), orderId, reason, Instant.now());
        outbox.append(result.eventId(), orderId, "inventory-rejected", result);
        return true;
    }
}
