package com.lylecommerce.inventory.domain;

import java.util.List;
import java.util.UUID;

public interface InventoryReservationRepository {
    record ReservedItem(UUID productId, int quantity) {}
    boolean tryClaimOrder(UUID orderId);
    void markRejected(UUID orderId, String reason);
    void recordItems(UUID orderId, List<ReservedItem> items);
    // Locks reservation row, returns null when already released or absent.
    List<ReservedItem> lockActiveItems(UUID orderId);
    void markReleased(UUID orderId);
}
