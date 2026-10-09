package com.lylecommerce.inventory.messaging;

import com.lylecommerce.inventory.application.ReserveInventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCreatedEventListenerTest {

    private final ReserveInventoryService service = mock(ReserveInventoryService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final OrderCreatedEventListener listener = new OrderCreatedEventListener(service, kafka);

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, Instant.now(), List.of());
    }

    @Test
    void duplicateDoesNotPublishEvent() {
        OrderCreatedEvent event = event();
        when(service.reserve(event)).thenReturn(false);
        listener.handleOrderCreated(event);
        verifyNoInteractions(kafka);
    }

    @Test
    void newlyReservedOrderPublishesEvent() {
        OrderCreatedEvent event = event();
        when(service.reserve(event)).thenReturn(true);
        when(kafka.send(eq("inventory-reserved"), eq(event.orderId().toString()),
                any(InventoryReservedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        listener.handleOrderCreated(event);
        verify(kafka).send(eq("inventory-reserved"), eq(event.orderId().toString()),
                any(InventoryReservedEvent.class));
    }
}
