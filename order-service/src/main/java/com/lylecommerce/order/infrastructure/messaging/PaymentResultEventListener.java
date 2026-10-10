package com.lylecommerce.order.infrastructure.messaging;
import com.lylecommerce.order.application.OrderWorkflowService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class PaymentResultEventListener {
    private final OrderWorkflowService workflow;
    public PaymentResultEventListener(OrderWorkflowService workflow) { this.workflow = workflow; }
    @KafkaListener(topics = "payment-result", groupId = "order-service-payment", containerFactory = "paymentKafkaListenerContainerFactory")
    public void onPaymentResult(PaymentResultEvent event) { workflow.paymentResult(event); }
}
