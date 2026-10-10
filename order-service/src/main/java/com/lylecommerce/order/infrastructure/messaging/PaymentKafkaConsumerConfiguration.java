package com.lylecommerce.order.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import java.util.HashMap;

@Configuration
public class PaymentKafkaConsumerConfiguration {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentResultEvent> paymentKafkaListenerContainerFactory(KafkaProperties properties) {
        return factory(properties, PaymentResultEvent.class);
    }
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryRejectedEvent> rejectionKafkaListenerContainerFactory(KafkaProperties properties) {
        return factory(properties, InventoryRejectedEvent.class);
    }
    private <T> ConcurrentKafkaListenerContainerFactory<String, T> factory(KafkaProperties properties, Class<T> eventType) {
        var configuration = new HashMap<>(properties.buildConsumerProperties());
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuration.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, eventType.getName());
        configuration.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(configuration));
        return factory;
    }
}
