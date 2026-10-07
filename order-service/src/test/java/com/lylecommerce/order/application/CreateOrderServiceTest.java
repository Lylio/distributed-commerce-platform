package com.lylecommerce.order.application;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderRepository;
import com.lylecommerce.order.domain.OrderStatus;
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

        OrderRepository repository =
                mock(OrderRepository.class);

        when(repository.save(any(Order.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        CreateOrderService service =
                new CreateOrderService(repository);

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

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
    }
}
