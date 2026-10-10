package com.lylecommerce.inventory.messaging;
import com.lylecommerce.inventory.application.ReserveInventoryService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
class OrderCreatedEventListenerTest {
    @Test void delegatesOutcomeAndPublicationToTransactionalService() {
        var service = mock(ReserveInventoryService.class);
        var event = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, Instant.now(), List.of());
        new OrderCreatedEventListener(service).handleOrderCreated(event);
        verify(service).reserve(event);
    }
}
