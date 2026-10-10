package com.lylecommerce.order.monitoring;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import static org.mockito.Mockito.*;

class MonitoringKafkaObserverTest {
    @Test void observesBrokerRecordWithoutClaimingBusinessConsumptionOrStoringCustomerData() {
        var store = mock(MonitoringStore.class);
        var observer = new MonitoringKafkaObserver(store, new ObjectMapper());
        var record = new ConsumerRecord<String, String>("order-created", 2, 42, "order", """
                {"eventId":"event-a","orderId":"11111111-1111-1111-1111-111111111111",
                 "customerId":"sensitive","items":[{"productName":"private"}]}
                """);
        observer.observe(record);
        verify(store).save(eq("kafka:order-created:2:42"), eq("11111111-1111-1111-1111-111111111111"),
                eq("OrderCreatedEvent"), eq("order-service"), eq("kafka"), eq("KAFKA_OBSERVED"), eq("OBSERVED"),
                any(Instant.class), eq("event-a"), eq("order-created"), eq(2), eq(42L));
        verifyNoMoreInteractions(store);
    }
    @Test void persistenceFailureIsPropagatedSoTheMonitoringConsumerDoesNotSilentlyAcknowledgeIt() {
        var store = mock(MonitoringStore.class);
        doThrow(new IllegalStateException("database down")).when(store).save(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt(), anyLong());
        var observer = new MonitoringKafkaObserver(store, new ObjectMapper());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> observer.observe(new ConsumerRecord<>("payment-result", 0, 1, "key", "{\"eventId\":\"e\",\"orderId\":\"11111111-1111-1111-1111-111111111111\",\"status\":\"FAILED\"}")));
    }
}
