package com.lylecommerce.inventory.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryItemTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Test
    void shouldReserveAvailableInventory() {

        InventoryItem item = new InventoryItem(
                PRODUCT_ID, 10, 0
        );

        item.reserve(3);

        assertEquals(7, item.getAvailableQuantity());
        assertEquals(3, item.getReservedQuantity());
    }

    @Test
    void shouldReleaseReservedInventory() {

        InventoryItem item = new InventoryItem(
                PRODUCT_ID, 7, 3
        );

        item.release(2);

        assertEquals(9, item.getAvailableQuantity());
        assertEquals(1, item.getReservedQuantity());
    }

    @Test
    void shouldRejectReservationWhenStockIsInsufficient() {

        InventoryItem item = new InventoryItem(
                PRODUCT_ID, 5, 0
        );

        assertThrows(
                IllegalStateException.class,
                () -> item.reserve(6)
        );

        assertEquals(5, item.getAvailableQuantity());
        assertEquals(0, item.getReservedQuantity());
    }

    @Test
    void shouldRejectInvalidReservationQuantity() {

        InventoryItem item = new InventoryItem(
                PRODUCT_ID, 10, 0
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> item.reserve(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> item.reserve(-1)
        );
    }

    @Test
    void shouldRejectReleasingMoreThanReserved() {

        InventoryItem item = new InventoryItem(
                PRODUCT_ID, 8, 2
        );

        assertThrows(
                IllegalStateException.class,
                () -> item.release(3)
        );

        assertEquals(8, item.getAvailableQuantity());
        assertEquals(2, item.getReservedQuantity());
    }

    @Test
    void shouldRejectNegativeInitialQuantities() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryItem(PRODUCT_ID, -1, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryItem(PRODUCT_ID, 0, -1)
        );
    }
}
