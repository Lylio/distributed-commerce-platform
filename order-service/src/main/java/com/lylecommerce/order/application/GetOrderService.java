package com.lylecommerce.order.application;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class GetOrderService {

    private final OrderRepository orderRepository;

    public GetOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrder(UUID orderId) {
        return orderRepository.findById(orderId);
    }
}
