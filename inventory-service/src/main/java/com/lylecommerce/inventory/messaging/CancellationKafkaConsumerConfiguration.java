package com.lylecommerce.inventory.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import java.util.HashMap;

@Configuration
public class CancellationKafkaConsumerConfiguration {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> cancellationKafkaListenerContainerFactory(KafkaProperties properties) {
        var configuration = new HashMap<>(properties.buildConsumerProperties());
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuration.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderCancelledEvent.class.getName());
        configuration.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(configuration));
        return factory;
    }
}
