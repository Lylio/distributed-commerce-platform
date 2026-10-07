package com.lylecommerce.order.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        UUID customerId,
        List<Item> items) {

    public record Item(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice) {
    }
}
