package com.lylecommerce.inventory.application;

import com.lylecommerce.inventory.domain.InventoryItem;
import com.lylecommerce.inventory.domain.InventoryRepository;
import com.lylecommerce.inventory.domain.InventoryReservationRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseInventoryServiceTest {
    private final InventoryRepository stock = mock(InventoryRepository.class);
    private final InventoryReservationRepository reservations = mock(InventoryReservationRepository.class);
    private final ReleaseInventoryService service = new ReleaseInventoryService(stock, reservations);
    private final UUID orderId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Test void failedPaymentRestoresStock() {
        InventoryItem item = new InventoryItem(productId, 8, 2);
        when(reservations.lockActiveItems(orderId)).thenReturn(List.of(new InventoryReservationRepository.ReservedItem(productId, 2)));
        when(stock.findByProductId(productId)).thenReturn(Optional.of(item));
        assertTrue(service.release(orderId));
        assertEquals(10, item.getAvailableQuantity());
        assertEquals(0, item.getReservedQuantity());
        verify(stock).save(item);
        verify(reservations).markReleased(orderId);
    }

    @Test void duplicateCancellationDoesNotTouchStock() {
        when(reservations.lockActiveItems(orderId)).thenReturn(null);
        assertFalse(service.release(orderId));
        verifyNoInteractions(stock);
        verify(reservations, never()).markReleased(any());
    }

    @Test void legacyReservationWithoutItemsFailsSafely() {
        when(reservations.lockActiveItems(orderId)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.release(orderId));
        verifyNoInteractions(stock);
    }
}
