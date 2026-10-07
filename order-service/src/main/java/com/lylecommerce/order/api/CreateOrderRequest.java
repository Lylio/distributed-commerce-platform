package com.lylecommerce.order.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        UUID customerId,
        List<Item> items) {

    public record Item(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice) {
    }
}
