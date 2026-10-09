package com.lylecommerce.inventory.domain;

import java.util.UUID;

public interface InventoryReservationRepository {

    boolean tryClaimOrder(UUID orderId);
}
