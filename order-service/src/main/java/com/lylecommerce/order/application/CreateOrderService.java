package com.lylecommerce.order.application;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.ProductRepository;
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

    private final ProductRepository products;
    public CreateOrderService(OrderRepository orderRepository, OrderEventPublisher orderEventPublisher, ProductRepository products) {
        this.products = products;
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Transactional
    public Order createOrder(CreateOrderCommand command) {

        if (command.customerId() == null) throw new IllegalArgumentException("Customer ID is required");
        if (command.items() == null || command.items().isEmpty() || command.items().size() > 100)
            throw new IllegalArgumentException("Order must contain between 1 and 100 items");
        List<OrderItem> items = command.items()
                .stream()
                .map(item -> {
                    if (item == null || item.productId() == null || item.quantity() < 1 || item.quantity() > 100)
                        throw new IllegalArgumentException("Each product requires a quantity between 1 and 100");
                    var product = products.findById(item.productId())
                            .orElseThrow(() -> new IllegalArgumentException("Product is unavailable: " + item.productId()));
                    // Legacy price/name fields are accepted for compatibility but never trusted.
                    return new OrderItem(product.id(), product.name(), item.quantity(), product.unitPrice());
                })
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
