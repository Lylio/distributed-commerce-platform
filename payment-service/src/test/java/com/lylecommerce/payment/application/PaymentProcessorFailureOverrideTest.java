package com.lylecommerce.payment.application;

import com.lylecommerce.payment.infrastructure.outbox.OutboxStore;
import com.lylecommerce.payment.messaging.PaymentResultEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentProcessorFailureOverrideTest {
    @Test void forceFailureOverridesSuccessAndRecordsMatchingEvent() {
        var jdbc = mock(JdbcTemplate.class);
        var outbox = mock(OutboxStore.class);
        var id = UUID.randomUUID();
        when(jdbc.update(anyString(), eq(id), eq("FAILED"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(id))).thenReturn("FAILED");

        assertEquals("FAILED", new PaymentProcessor(jdbc, outbox, "SUCCEEDED", true).process(id));

        var event = ArgumentCaptor.forClass(PaymentResultEvent.class);
        verify(outbox).append(any(), eq(id), eq("payment-result"), event.capture());
        assertEquals(id, event.getValue().orderId());
        assertEquals("FAILED", event.getValue().status());
        verify(outbox).append(eq(event.getValue().eventId()), eq(id), eq("payment-result"), eq(event.getValue()));
        assertNotNull(event.getValue().occurredAt());
    }

    @Test void forceFailurePreservesExistingSuccessfulPayment() {
        var jdbc = mock(JdbcTemplate.class);
        var outbox = mock(OutboxStore.class);
        var id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(id))).thenReturn("SUCCEEDED");

        assertEquals("SUCCEEDED", new PaymentProcessor(jdbc, outbox, "AUTO", true).process(id));

        verify(jdbc).update(anyString(), eq(id), eq("FAILED"));
        verifyNoInteractions(outbox);
    }
}
