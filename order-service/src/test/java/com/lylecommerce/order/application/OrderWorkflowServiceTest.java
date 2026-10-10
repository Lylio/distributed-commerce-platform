package com.lylecommerce.order.application;
import com.lylecommerce.order.domain.*;
import com.lylecommerce.order.infrastructure.messaging.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderWorkflowServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OrderWorkflowService workflow = new OrderWorkflowService(orders, jdbc, events);
    private Order order() {
        var order = Order.create(UUID.randomUUID(), List.of(new OrderItem(UUID.randomUUID(), "Keyboard", 1, BigDecimal.TEN)));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        return order;
    }
    @Test void earlyPaymentIsRecordedWithoutSkippingReservation() {
        var order = order();
        workflow.paymentResult(new PaymentResultEvent(UUID.randomUUID(), order.getId(), "SUCCEEDED", Instant.now()));
        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(jdbc).update(anyString(), eq(order.getId()), any(UUID.class), eq("SUCCEEDED"));
        verify(orders, never()).save(any());
    }
    @Test void failedPaymentCancelsAndRequestsCompensation() {
        var order = order(); order.reserveInventory();
        workflow.paymentResult(new PaymentResultEvent(UUID.randomUUID(), order.getId(), "FAILED", Instant.now()));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(events).publishEvent(isA(OrderCancelledEvent.class));
    }
    @Test void reservationReplayCannotRegressConfirmation() {
        var order = order(); order.reserveInventory(); order.startPayment(); order.confirm();
        workflow.inventoryReserved(order.getId());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verifyNoInteractions(jdbc, events);
        verify(orders, never()).save(any());
    }
    @Test void invalidPaymentStatusCannotMutateAnOrder() {
        assertThrows(IllegalArgumentException.class, () -> workflow.paymentResult(
                new PaymentResultEvent(UUID.randomUUID(), UUID.randomUUID(), "INVALID", Instant.now())));
        verifyNoInteractions(orders, jdbc, events);
    }
}
