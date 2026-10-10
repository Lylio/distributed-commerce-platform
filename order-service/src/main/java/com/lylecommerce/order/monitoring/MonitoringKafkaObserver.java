package com.lylecommerce.order.monitoring;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;

@Component
public class MonitoringKafkaObserver {
    private final MonitoringStore store;
    private final ObjectMapper mapper;
    public MonitoringKafkaObserver(MonitoringStore store, ObjectMapper mapper) { this.store = store; this.mapper = mapper; }
    @KafkaListener(topics = {"order-created", "inventory-reserved", "inventory-rejected", "payment-result", "order-cancelled"},
            groupId = "system-monitor-v1", containerFactory = "monitoringKafkaFactory")
    public void observe(ConsumerRecord<String, String> record) {
        var payload = mapper.readTree(record.value());
        String order = payload.path("orderId").asText();
        String id = payload.path("eventId").asText();
        String source = source(record.topic());
        String type = type(record.topic());
        // This observes the broker record; it says nothing about business consumer delivery.
        store.save("kafka:" + record.topic() + ":" + record.partition() + ":" + record.offset(), order, type,
                source, "kafka", "KAFKA_OBSERVED", payload.path("status").asText("OBSERVED"),
                Instant.ofEpochMilli(record.timestamp()), id, record.topic(), record.partition(), record.offset());
    }
    static String source(String topic) {
        return switch (topic) { case "order-created", "order-cancelled" -> "order-service";
            case "inventory-reserved", "inventory-rejected" -> "inventory-service"; default -> "payment-service"; };
    }
    static String type(String topic) {
        return switch (topic) { case "order-created" -> "OrderCreatedEvent"; case "order-cancelled" -> "OrderCancelledEvent";
            case "inventory-reserved" -> "InventoryReservedEvent"; case "inventory-rejected" -> "InventoryRejectedEvent";
            default -> "PaymentResultEvent"; };
    }
    @Configuration
    static class KafkaConfiguration {
        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> monitoringKafkaFactory(@Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
            var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
            factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                    ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100)));
            // Monitoring database outages retry only this independent observer group.
            factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
                    new org.springframework.util.backoff.FixedBackOff(2000, org.springframework.util.backoff.FixedBackOff.UNLIMITED_ATTEMPTS)));
            return factory;
        }
    }
}
