package com.lylecommerce.inventory.domain;

import java.util.UUID;

public class InventoryItem {

    private final UUID productId;
    private int availableQuantity;
    private int reservedQuantity;

    public InventoryItem(
            UUID productId,
            int availableQuantity,
            int reservedQuantity) {

        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }

        if (availableQuantity < 0 || reservedQuantity < 0) {
            throw new IllegalArgumentException(
                    "Inventory quantities cannot be negative"
            );
        }

        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    public void reserve(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Reservation quantity must be positive"
            );
        }

        if (quantity > availableQuantity) {
            throw new IllegalStateException(
                    "Insufficient inventory for product " + productId
            );
        }

        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    public void release(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Release quantity must be positive"
            );
        }

        if (quantity > reservedQuantity) {
            throw new IllegalStateException(
                    "Cannot release more than reserved quantity"
            );
        }

        reservedQuantity -= quantity;
        availableQuantity += quantity;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }
}
