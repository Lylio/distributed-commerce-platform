package com.lylecommerce.inventory.application;

import com.lylecommerce.inventory.domain.InventoryItem;
import com.lylecommerce.inventory.domain.InventoryRepository;
import com.lylecommerce.inventory.domain.InventoryReservationRepository;
import com.lylecommerce.inventory.messaging.OrderCreatedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReserveInventoryServiceTest {

    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final InventoryReservationRepository reservationRepository = mock(InventoryReservationRepository.class);
    private final ReserveInventoryService service =
            new ReserveInventoryService(inventoryRepository, reservationRepository);

    private final UUID orderId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent(UUID.randomUUID(), orderId, UUID.randomUUID(),
                new BigDecimal("79.99"), Instant.now(),
                List.of(new OrderCreatedEvent.Item(productId, "Keyboard", 2,
                        new BigDecimal("39.995"))));
    }

    @Test
    void firstDeliveryReservesStock() {
        InventoryItem stock = new InventoryItem(productId, 10, 0);
        when(reservationRepository.tryClaimOrder(orderId)).thenReturn(true);
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(stock));

        assertTrue(service.reserve(event()));
        assertEquals(8, stock.getAvailableQuantity());
        assertEquals(2, stock.getReservedQuantity());
        verify(inventoryRepository).save(stock);
    }

    @Test
    void duplicateDeliveryDoesNotTouchStock() {
        when(reservationRepository.tryClaimOrder(orderId)).thenReturn(false);

        assertFalse(service.reserve(event()));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void missingProductFailsRatherThanReportingSuccess() {
        when(reservationRepository.tryClaimOrder(orderId)).thenReturn(true);
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.reserve(event()));
        verify(inventoryRepository, never()).save(any());
    }
}
