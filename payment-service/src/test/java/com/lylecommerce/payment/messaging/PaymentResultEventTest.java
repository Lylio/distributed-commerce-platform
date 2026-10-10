package com.lylecommerce.payment.messaging;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PaymentResultEventTest {
 @Test void storesResultFields() {
  UUID orderId=UUID.randomUUID();
  PaymentResultEvent event=new PaymentResultEvent(UUID.randomUUID(),orderId,"SUCCEEDED",Instant.now());
  assertEquals(orderId,event.orderId());assertEquals("SUCCEEDED",event.status());
 }
}
