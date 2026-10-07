package com.lylecommerce.order.infrastructure.persistence;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderItem;

import java.util.List;
import java.util.UUID;

public final class OrderPersistenceMapper {

    private OrderPersistenceMapper() {
    }

    public static OrderJpaEntity toJpaEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );

        for (OrderItem item : order.getItems()) {
            OrderItemJpaEntity itemEntity =
                    new OrderItemJpaEntity(
                            UUID.randomUUID(),
                            item.getProductId(),
                            item.getProductName(),
                            item.getQuantity(),
                            item.getUnitPrice()
                    );

            entity.addItem(itemEntity);
        }

        return entity;
    }

    public static Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems()
                .stream()
                .map(OrderPersistenceMapper::toDomainItem)
                .toList();

        return Order.rehydrate(
                entity.getId(),
                entity.getCustomerId(),
                items,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static OrderItem toDomainItem(
            OrderItemJpaEntity entity) {

        return new OrderItem(
                entity.getProductId(),
                entity.getProductName(),
                entity.getQuantity(),
                entity.getUnitPrice()
        );
    }
}
