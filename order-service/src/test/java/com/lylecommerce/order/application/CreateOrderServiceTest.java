package com.lylecommerce.order.application;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.Product;
import com.lylecommerce.order.domain.ProductRepository;
import com.lylecommerce.order.domain.OrderRepository;
import com.lylecommerce.order.domain.OrderStatus;
import com.lylecommerce.order.infrastructure.messaging.OrderEventPublisher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateOrderServiceTest {

    @Test
    void shouldCreateAndSaveOrder() {

        OrderEventPublisher eventPublisher =
                mock(OrderEventPublisher.class);

        OrderRepository repository =
                mock(OrderRepository.class);

        when(repository.save(any(Order.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        ProductRepository products = mock(ProductRepository.class);
        CreateOrderService service =
                new CreateOrderService(
                        repository,
                        eventPublisher, products
                );

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(products.findById(productId)).thenReturn(java.util.Optional.of(new Product(productId,
                "Mechanical Keyboard", "Fixture", "Workspace", "Description", List.of(), "keyboard", "sage", new BigDecimal("79.99"), "GBP")));

        CreateOrderCommand command =
                new CreateOrderCommand(
                        customerId,
                        List.of(
                                new CreateOrderCommand.Item(
                                        productId,
                                        "Mechanical Keyboard",
                                        2,
                                        new BigDecimal("79.99")
                                )
                        )
                );

        Order order = service.createOrder(command);

        assertNotNull(order.getId());
        assertEquals(customerId, order.getCustomerId());
        assertEquals(OrderStatus.PENDING, order.getStatus());

        assertEquals(
                new BigDecimal("159.98"),
                order.calculateTotal()
        );

        assertEquals(1, order.getItems().size());

        assertEquals(
                productId,
                order.getItems().getFirst().getProductId()
        );

        verify(repository, times(1))
                .save(any(Order.class));
        verify(eventPublisher, times(1))
                .publishOrderCreated(any(com.lylecommerce.order.infrastructure.messaging.OrderCreatedEvent.class));
    }
}
