package com.lylecommerce.order.infrastructure.persistence;

import com.lylecommerce.order.api.OrderResponse;
import com.lylecommerce.order.application.CreateOrderCommand;
import com.lylecommerce.order.application.CreateOrderService;
import com.lylecommerce.order.domain.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

/** Demo storefront. Product pricing is authoritative on the server, never supplied by the browser. */
@RestController
@RequestMapping("/api/store")
public class StorefrontController {
    public record Product(UUID id, String name, String category, String description, BigDecimal price, String emoji) {}
    public record CheckoutItem(UUID productId, int quantity) {}
    public record CheckoutRequest(UUID customerId, List<CheckoutItem> items) {}

    private final com.lylecommerce.order.domain.ProductRepository products;

    private final CreateOrderService createOrderService;
    private final SpringDataOrderRepository orderRepository;

    public StorefrontController(CreateOrderService createOrderService, SpringDataOrderRepository orderRepository, com.lylecommerce.order.domain.ProductRepository products) {
        this.products = products;
        this.createOrderService = createOrderService;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/products")
    public List<Product> products() { return products.findAll().stream().map(p -> new Product(p.id(), p.name(), p.category(), p.description(), p.unitPrice(), "")).toList(); }

    @GetMapping("/orders")
    public List<OrderResponse> orders() {
        return orderRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream().limit(100).map(OrderPersistenceMapper::toDomain).map(OrderResponse::from).toList();
    }

    @GetMapping("/orders/{id}")
    public OrderResponse order(@PathVariable UUID id) {
        return orderRepository.findById(id).map(OrderPersistenceMapper::toDomain).map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@RequestBody CheckoutRequest request) {
        if (request == null || request.customerId() == null || request.items() == null || request.items().isEmpty() || request.items().size() > 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer and 1–30 items are required");
        List<CreateOrderCommand.Item> items = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (CheckoutItem line : request.items()) {
            if (line == null || line.productId() == null || line.quantity() < 1 || line.quantity() > 20 || !seen.add(line.productId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or duplicate item; quantity must be 1–20");
            com.lylecommerce.order.domain.Product product = products.findById(line.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown product"));
            items.add(new CreateOrderCommand.Item(product.id(), product.name(), line.quantity(), product.unitPrice()));
        }
        Order order = createOrderService.createOrder(new CreateOrderCommand(request.customerId(), items));
        return OrderResponse.from(order);
    }
}
