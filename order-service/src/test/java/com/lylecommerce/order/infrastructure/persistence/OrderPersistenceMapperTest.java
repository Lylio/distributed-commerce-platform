package com.lylecommerce.order.infrastructure.persistence;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderItem;
import com.lylecommerce.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderPersistenceMapperTest {

    @Test
    void shouldMapDomainOrderToJpaEntity() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        OrderItem item = new OrderItem(
                productId,
                "Mechanical Keyboard",
                2,
                new BigDecimal("79.99")
        );

        Order order = Order.create(
                customerId,
                List.of(item)
        );

        OrderJpaEntity entity =
                OrderPersistenceMapper.toJpaEntity(order);

        assertEquals(order.getId(), entity.getId());
        assertEquals(customerId, entity.getCustomerId());
        assertEquals(OrderStatus.PENDING, entity.getStatus());
        assertEquals(1, entity.getItems().size());

        OrderItemJpaEntity itemEntity =
                entity.getItems().getFirst();

        assertEquals(productId, itemEntity.getProductId());
        assertEquals(
                "Mechanical Keyboard",
                itemEntity.getProductName()
        );
        assertEquals(2, itemEntity.getQuantity());
        assertEquals(
                new BigDecimal("79.99"),
                itemEntity.getUnitPrice()
        );

        assertSame(entity, itemEntity.getOrder());
    }

    @Test
    void shouldMapJpaEntityToDomainOrder() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Instant createdAt =
                Instant.parse("2026-10-06T18:00:00Z");

        Instant updatedAt =
                Instant.parse("2026-10-06T18:05:00Z");

        OrderJpaEntity entity = new OrderJpaEntity(
                orderId,
                customerId,
                OrderStatus.CONFIRMED,
                createdAt,
                updatedAt
        );

        entity.addItem(
                new OrderItemJpaEntity(
                        UUID.randomUUID(),
                        productId,
                        "Mechanical Keyboard",
                        2,
                        new BigDecimal("79.99")
                )
        );

        Order order =
                OrderPersistenceMapper.toDomain(entity);

        assertEquals(orderId, order.getId());
        assertEquals(customerId, order.getCustomerId());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(createdAt, order.getCreatedAt());
        assertEquals(updatedAt, order.getUpdatedAt());

        assertEquals(1, order.getItems().size());

        OrderItem item = order.getItems().getFirst();

        assertEquals(productId, item.getProductId());
        assertEquals("Mechanical Keyboard", item.getProductName());
        assertEquals(2, item.getQuantity());
        assertEquals(
                new BigDecimal("79.99"),
                item.getUnitPrice()
        );
    }
}
