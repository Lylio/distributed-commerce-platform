package com.lylecommerce.order.infrastructure.messaging;
import com.lylecommerce.order.application.OrderWorkflowService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
class PaymentResultEventListenerTest {
    @Test void delegatesToTransactionalWorkflow() {
        var workflow = mock(OrderWorkflowService.class);
        var event = new PaymentResultEvent(UUID.randomUUID(), UUID.randomUUID(), "FAILED", Instant.now());
        new PaymentResultEventListener(workflow).onPaymentResult(event);
        verify(workflow).paymentResult(event);
    }
}
