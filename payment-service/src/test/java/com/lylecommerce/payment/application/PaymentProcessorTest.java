package com.lylecommerce.payment.application;
import com.lylecommerce.payment.infrastructure.outbox.OutboxStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PaymentProcessorTest {
    @Test void duplicatePaymentUsesRecordedDecisionAndDoesNotCreateAnotherEvent() {
        var jdbc = mock(JdbcTemplate.class); var outbox = mock(OutboxStore.class);
        var id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(id))).thenReturn("FAILED");
        assertEquals("FAILED", new PaymentProcessor(jdbc, outbox, "SUCCEEDED").process(id));
        verifyNoInteractions(outbox);
    }
    @Test void newDecisionAndEventAreRecordedTogether() {
        var jdbc = mock(JdbcTemplate.class); var outbox = mock(OutboxStore.class);
        var id = UUID.randomUUID();
        when(jdbc.update(anyString(), eq(id), eq("FAILED"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(id))).thenReturn("FAILED");
        assertEquals("FAILED", new PaymentProcessor(jdbc, outbox, "FAILED").process(id));
        verify(outbox).append(any(), eq(id), eq("payment-result"), any());
    }
}
