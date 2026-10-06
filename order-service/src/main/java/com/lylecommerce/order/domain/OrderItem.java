package com.lylecommerce.order.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class OrderItem {

    private final UUID productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;

    public OrderItem(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice) {

        this.productId = Objects.requireNonNull(
                productId,
                "Product ID must not be null"
        );

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException(
                    "Product name must not be blank"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Unit price must not be negative"
            );
        }

        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal calculateTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
