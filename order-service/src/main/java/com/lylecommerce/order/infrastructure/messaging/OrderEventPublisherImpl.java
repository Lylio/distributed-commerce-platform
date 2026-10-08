package com.lylecommerce.order.infrastructure.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisherImpl implements OrderEventPublisher {

    private static final String ORDER_CREATED_TOPIC = "order-created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisherImpl(
            KafkaTemplate<String, Object> kafkaTemplate) {

        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishOrderCreated(OrderCreatedEvent event) {

        kafkaTemplate.send(
                ORDER_CREATED_TOPIC,
                event.orderId().toString(),
                event
        ).join();
    }
}