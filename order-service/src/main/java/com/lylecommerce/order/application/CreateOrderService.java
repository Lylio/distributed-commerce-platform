package com.lylecommerce.order.application;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderItem;
import com.lylecommerce.order.domain.OrderRepository;
import com.lylecommerce.order.infrastructure.messaging.OrderCreatedEvent;
import com.lylecommerce.order.infrastructure.messaging.OrderEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CreateOrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public CreateOrderService(OrderRepository orderRepository, OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Transactional
    public Order createOrder(CreateOrderCommand command) {

        List<OrderItem> items = command.items()
                .stream()
                .map(item -> new OrderItem(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.unitPrice()
                ))
                .toList();

        Order order = Order.create(
                command.customerId(),
                items
        );

        Order savedOrder = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                savedOrder.calculateTotal(),
                Instant.now(),
                savedOrder.getItems().stream()
                        .map(item -> new OrderCreatedEvent.Item(
                                item.getProductId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getUnitPrice()
                        ))
                        .toList()
        );

        orderEventPublisher.publishOrderCreated(event);

        return savedOrder;
    }
}
