package com.lylecommerce.order.infrastructure.persistence;

import com.lylecommerce.order.OrderServiceApplication;
import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderItem;
import com.lylecommerce.order.domain.OrderRepository;
import com.lylecommerce.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = OrderServiceApplication.class)
@Transactional
class JpaOrderRepositoryAdapterIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldSaveAndRetrieveOrder() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        OrderItem item = new OrderItem(
                productId,
                "Mechanical Keyboard",
                2,
                new BigDecimal("79.99")
        );

        Order order = Order.create(
                customerId,
                List.of(item)
        );

        Order savedOrder = orderRepository.save(order);

        Optional<Order> retrievedOrder =
                orderRepository.findById(savedOrder.getId());

        assertTrue(retrievedOrder.isPresent());

        Order found = retrievedOrder.orElseThrow();

        assertEquals(savedOrder.getId(), found.getId());
        assertEquals(customerId, found.getCustomerId());
        assertEquals(OrderStatus.PENDING, found.getStatus());

        assertEquals(
                new BigDecimal("159.98"),
                found.calculateTotal()
        );

        assertEquals(1, found.getItems().size());

        OrderItem foundItem = found.getItems().getFirst();

        assertEquals(productId, foundItem.getProductId());
        assertEquals(
                "Mechanical Keyboard",
                foundItem.getProductName()
        );
        assertEquals(2, foundItem.getQuantity());
        assertEquals(
                new BigDecimal("79.99"),
                foundItem.getUnitPrice()
        );
    }
}
