package com.lylecommerce.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderItemTest {

    @Test
    void shouldCreateValidOrderItem() {
        UUID productId = UUID.randomUUID();

        OrderItem item = new OrderItem(
                productId,
                "Mechanical Keyboard",
                2,
                new BigDecimal("79.99")
        );

        assertEquals(productId, item.getProductId());
        assertEquals("Mechanical Keyboard", item.getProductName());
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("79.99"), item.getUnitPrice());
    }

    @Test
    void shouldCalculateItemTotal() {
        OrderItem item = new OrderItem(
                UUID.randomUUID(),
                "Mechanical Keyboard",
                2,
                new BigDecimal("79.99")
        );

        assertEquals(
                new BigDecimal("159.98"),
                item.calculateTotal()
        );
    }

    @Test
    void shouldRejectZeroQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderItem(
                        UUID.randomUUID(),
                        "Mechanical Keyboard",
                        0,
                        new BigDecimal("79.99")
                )
        );
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderItem(
                        UUID.randomUUID(),
                        "Mechanical Keyboard",
                        -1,
                        new BigDecimal("79.99")
                )
        );
    }

    @Test
    void shouldRejectNegativePrice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderItem(
                        UUID.randomUUID(),
                        "Mechanical Keyboard",
                        1,
                        new BigDecimal("-1.00")
                )
        );
    }

    @Test
    void shouldRejectBlankProductName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderItem(
                        UUID.randomUUID(),
                        " ",
                        1,
                        new BigDecimal("79.99")
                )
        );
    }
}