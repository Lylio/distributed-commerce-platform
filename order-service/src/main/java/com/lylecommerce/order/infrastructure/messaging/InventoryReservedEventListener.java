package com.lylecommerce.order.infrastructure.messaging;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderRepository;
import com.lylecommerce.order.domain.OrderStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryReservedEventListener {

    private final OrderRepository orderRepository;

    public InventoryReservedEventListener(
            OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @KafkaListener(
            topics = "inventory-reserved",
            groupId = "order-service"
    )
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {

        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found: " + event.orderId()
                ));

        // Avoid applying the same event twice.
        if (order.getStatus() == OrderStatus.INVENTORY_RESERVED) {
            return;
        }

        order.reserveInventory();
        orderRepository.save(order);

        System.out.println(
                "Order status updated to INVENTORY_RESERVED: "
                        + order.getId()
        );
    }
}
