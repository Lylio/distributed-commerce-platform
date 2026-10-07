package com.lylecommerce.order.application;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderItem;
import com.lylecommerce.order.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CreateOrderService {

    private final OrderRepository orderRepository;

    public CreateOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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

        return orderRepository.save(order);
    }
}
