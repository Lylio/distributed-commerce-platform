package com.lylecommerce.order.api;

import com.lylecommerce.order.application.CreateOrderCommand;
import com.lylecommerce.order.application.CreateOrderService;
import com.lylecommerce.order.domain.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.lylecommerce.order.application.GetOrderService;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final GetOrderService getOrderService;

    private final CreateOrderService createOrderService;

    public OrderController(
            CreateOrderService createOrderService,
            GetOrderService getOrderService) {

        this.createOrderService = createOrderService;
        this.getOrderService = getOrderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @RequestBody CreateOrderRequest request) {

        if (request.items() == null || request.items().stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Order items are required");
        List<CreateOrderCommand.Item> items =
                request.items()
                        .stream()
                        .map(item ->
                                new CreateOrderCommand.Item(
                                        item.productId(),
                                        item.productName(),
                                        item.quantity(),
                                        item.unitPrice()
                                )
                        )
                        .toList();

        CreateOrderCommand command =
                new CreateOrderCommand(
                        request.customerId(),
                        items
                );

        Order order =
                createOrderService.createOrder(command);

        return OrderResponse.from(order);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            @PathVariable("orderId") UUID orderId) {

        Order order = getOrderService
                .getOrder(orderId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found"
                        )
                );

        return OrderResponse.from(order);
    }
}
