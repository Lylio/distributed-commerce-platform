package com.lylecommerce.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {

    InventoryItem save(InventoryItem item);

    Optional<InventoryItem> findByProductId(UUID productId);
}
