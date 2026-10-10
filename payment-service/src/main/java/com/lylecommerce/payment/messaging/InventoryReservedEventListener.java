package com.lylecommerce.payment.messaging;
import com.lylecommerce.payment.application.PaymentProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class InventoryReservedEventListener {
    private final PaymentProcessor processor;
    public InventoryReservedEventListener(PaymentProcessor processor) { this.processor = processor; }
    @KafkaListener(topics = "inventory-reserved", groupId = "payment-service")
    public void onInventoryReserved(InventoryReservedEvent event) { processor.process(event.orderId()); }
}
