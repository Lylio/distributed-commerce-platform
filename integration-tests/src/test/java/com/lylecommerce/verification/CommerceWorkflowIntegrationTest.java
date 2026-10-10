package com.lylecommerce.verification;

import com.lylecommerce.order.OrderServiceApplication;
import com.lylecommerce.inventory.InventoryServiceApplication;
import com.lylecommerce.payment.PaymentServiceApplication;
import com.lylecommerce.order.application.CreateOrderCommand;
import com.lylecommerce.order.application.CreateOrderService;
import com.lylecommerce.order.application.GetOrderService;
import com.lylecommerce.order.application.OrderWorkflowService;
import com.lylecommerce.order.domain.*;
import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.infrastructure.messaging.*;
import com.lylecommerce.order.infrastructure.outbox.OutboxPublisher;
import com.lylecommerce.inventory.application.ReserveInventoryService;
import com.lylecommerce.inventory.application.ReleaseInventoryService;
import com.lylecommerce.payment.application.PaymentProcessor;
import org.junit.jupiter.api.*;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class CommerceWorkflowIntegrationTest {
    @Container static final PostgreSQLContainer<?> orderDb = new PostgreSQLContainer<>("postgres:17");
    @Container static final PostgreSQLContainer<?> inventoryDb = new PostgreSQLContainer<>("postgres:17");
    @Container static final PostgreSQLContainer<?> paymentDb = new PostgreSQLContainer<>("postgres:17");
    @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.0");
    static ConfigurableApplicationContext orders, inventory, payments;
    static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID EMPTY = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeAll static void startServices() {
        orders = start(OrderServiceApplication.class, "order", orderDb,
                "com.lylecommerce.order.infrastructure.messaging.InventoryReservedEvent");
        inventory = start(InventoryServiceApplication.class, "inventory", inventoryDb,
                "com.lylecommerce.inventory.messaging.OrderCreatedEvent");
        payments = start(PaymentServiceApplication.class, "payment", paymentDb,
                "com.lylecommerce.payment.messaging.InventoryReservedEvent");
    }
    static ConfigurableApplicationContext start(Class<?> app, String service, PostgreSQLContainer<?> db, String inputType) {
        return new SpringApplicationBuilder(app).web(WebApplicationType.NONE).run(
                "--spring.application.name=" + service + "-service",
                "--spring.profiles.active=dev",
                "--spring.datasource.url=" + db.getJdbcUrl(),
                "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(),
                // Three service jars share a test classpath, but each owns its migrations/database.
                "--spring.flyway.locations=filesystem:" + Path.of("../" + service + "-service/src/main/resources/db/migration").toAbsolutePath(),
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.open-in-view=false",
                "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "--spring.kafka.consumer.group-id=" + service + "-service",
                "--spring.kafka.consumer.auto-offset-reset=earliest",
                "--spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "--spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JacksonJsonDeserializer",
                "--spring.kafka.consumer.properties.spring.json.value.default.type=" + inputType,
                "--spring.kafka.consumer.properties.spring.json.use.type.headers=false",
                "--spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "--spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
                "--outbox.scheduling-enabled=false",
                "--logging.level.root=WARN");
    }
    @AfterAll static void stopServices() {
        if (payments != null) payments.close();
        if (inventory != null) inventory.close();
        if (orders != null) orders.close();
    }
    static JdbcTemplate jdbc(ConfigurableApplicationContext context) { return context.getBean(JdbcTemplate.class); }
    static Order get(UUID id) { return orders.getBean(GetOrderService.class).getOrder(id).orElseThrow(); }
    static void pump() {
        orders.getBean(OutboxPublisher.class).publishPending();
        inventory.getBean(com.lylecommerce.inventory.infrastructure.outbox.OutboxPublisher.class).publishPending();
        payments.getBean(com.lylecommerce.payment.infrastructure.outbox.OutboxPublisher.class).publishPending();
    }
    static void await(BooleanSupplier ready) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) { pump(); Thread.sleep(50); }
        assertTrue(ready.getAsBoolean(), "Workflow did not converge before timeout");
    }
    static OrderCreatedEvent event(UUID id, List<OrderItem> items) {
        return new OrderCreatedEvent(UUID.randomUUID(), id, UUID.randomUUID(), BigDecimal.TEN, Instant.now(),
                items.stream().map(i -> new OrderCreatedEvent.Item(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice())).toList());
    }
    // Known UUIDs exercise the existing deterministic AUTO simulator (hash modulo 10).
    static UUID seed(boolean paymentFails, UUID... products) {
        UUID id = new UUID(0, paymentFails ? 10 : 11);
        // Avoid collisions across tests while retaining a deterministic hash outcome.
        long counter = sequence++;
        id = new UUID(counter << 32 | counter, paymentFails ? 10 : 11);
        assertEquals(paymentFails, Math.floorMod(id.hashCode(), 10) == 0);
        List<OrderItem> items = Arrays.stream(products).map(p -> new OrderItem(p, "Fixture", 2, BigDecimal.TEN)).toList();
        UUID orderId = id;
        new TransactionTemplate(orders.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            orders.getBean(OrderRepository.class).save(Order.rehydrate(orderId, UUID.randomUUID(), items,
                    OrderStatus.PENDING, Instant.now(), Instant.now()));
            orders.getBean(OrderEventPublisher.class).publishOrderCreated(event(orderId, items));
        });
        return orderId;
    }
    static long sequence = 1;
    static int available() { return jdbc(inventory).queryForObject("SELECT available_quantity FROM inventory_items WHERE product_id = ?", Integer.class, PRODUCT); }

    static void awaitConsumed(RecordMetadata record, String... groups) throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            await(() -> {
                try {
                    for (String group : groups) {
                        var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                        var offset = offsets.get(new TopicPartition(record.topic(), record.partition()));
                        if (offset == null || offset.offset() <= record.offset()) return false;
                    }
                    return true;
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
        }
    }

    @Test void successfulOrderUsesKafkaAndThreeDatabases() throws Exception {
        int before = available(); UUID id = seed(false, PRODUCT);
        await(() -> get(id).getStatus() == OrderStatus.CONFIRMED);
        assertEquals(before - 2, available());
        assertEquals("SUCCEEDED", jdbc(payments).queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, id));
        assertEquals(1, jdbc(payments).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id));
    }
    @Test void paymentFailureCancelsAndRestoresStockExactlyOnce() throws Exception {
        int before = available(); UUID id = seed(true, PRODUCT);
        await(() -> get(id).getStatus() == OrderStatus.CANCELLED
                && jdbc(inventory).queryForObject("SELECT count(*) FROM inventory_reservations WHERE order_id = ? AND released_at IS NOT NULL", Integer.class, id) == 1);
        assertEquals(before, available());
        assertFalse(inventory.getBean(ReleaseInventoryService.class).release(id));
        @SuppressWarnings("unchecked") KafkaTemplate<String,Object> sender = orders.getBean(KafkaTemplate.class);
        var replay = sender.send("order-cancelled", id.toString(), new OrderCancelledEvent(UUID.randomUUID(), id, Instant.now()))
                .get(10, TimeUnit.SECONDS).getRecordMetadata();
        awaitConsumed(replay, "inventory-service-cancellation");
        assertEquals(before, available());
        assertEquals(1, jdbc(orders).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND topic = 'order-cancelled'", Integer.class, id));
    }
    @Test void multiItemStockRejectionChangesNoStockAndNeverRequestsPayment() throws Exception {
        int before = available(); UUID id = seed(false, PRODUCT, EMPTY);
        await(() -> get(id).getStatus() == OrderStatus.CANCELLED);
        assertEquals(before, available());
        assertEquals("REJECTED", jdbc(inventory).queryForObject("SELECT outcome FROM inventory_reservations WHERE order_id = ?", String.class, id));
        assertEquals(0, jdbc(payments).queryForObject("SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, id));
        assertEquals(0, jdbc(inventory).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND topic = 'inventory-reserved'", Integer.class, id));
        assertTrue(jdbc(orders).queryForObject("SELECT cancellation_reason FROM orders WHERE id = ?", String.class, id).startsWith("INSUFFICIENT_STOCK"));
    }
    @Test void missingProductIsARecordedBusinessRejection() throws Exception {
        UUID id = seed(false, UUID.randomUUID());
        await(() -> get(id).getStatus() == OrderStatus.CANCELLED);
        assertTrue(jdbc(orders).queryForObject("SELECT cancellation_reason FROM orders WHERE id = ?", String.class, id).startsWith("PRODUCT_NOT_FOUND"));
    }
    @Test void duplicateKafkaMessagesDoNotRepeatStockOrPaymentEffects() throws Exception {
        UUID id = seed(false, PRODUCT); await(() -> get(id).getStatus() == OrderStatus.CONFIRMED);
        int stock = available();
        var created = event(id, get(id).getItems());
        @SuppressWarnings("unchecked") KafkaTemplate<String,Object> sender = orders.getBean(KafkaTemplate.class);
        var createdOffset = sender.send("order-created", id.toString(), created).get(10, TimeUnit.SECONDS).getRecordMetadata();
        var reservedOffset = sender.send("inventory-reserved", id.toString(), new InventoryReservedEvent(UUID.randomUUID(), id, Instant.now()))
                .get(10, TimeUnit.SECONDS).getRecordMetadata();
        awaitConsumed(createdOffset, "inventory-service");
        awaitConsumed(reservedOffset, "order-service", "payment-service");
        assertEquals(stock, available());
        assertEquals(OrderStatus.CONFIRMED, get(id).getStatus());
        assertEquals(1, jdbc(inventory).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id));
        assertEquals(1, jdbc(payments).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id));
    }
    @Test void earlyPaymentSurvivesUntilReservationAndConflictsAreRejected() throws Exception {
        UUID id = seed(false, PRODUCT);
        var workflow = orders.getBean(OrderWorkflowService.class);
        workflow.paymentResult(new PaymentResultEvent(UUID.randomUUID(), id, "SUCCEEDED", Instant.now()));
        assertEquals(OrderStatus.PENDING, get(id).getStatus());
        await(() -> get(id).getStatus() == OrderStatus.CONFIRMED);
        assertThrows(IllegalStateException.class, () -> workflow.paymentResult(
                new PaymentResultEvent(UUID.randomUUID(), id, "FAILED", Instant.now())));
        assertEquals("SUCCEEDED", jdbc(orders).queryForObject("SELECT status FROM order_payment_results WHERE order_id = ?", String.class, id));
    }
    @Test void failedPublicationRetainsPayloadAndEventIdForRecovery() throws Exception {
        UUID id = seed(false, PRODUCT);
        String payload = jdbc(orders).queryForObject("SELECT payload::text FROM outbox_events WHERE aggregate_id = ?", String.class, id);
        UUID eventId = jdbc(orders).queryForObject("SELECT event_id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id);
        @SuppressWarnings("unchecked") KafkaTemplate<String,Object> failedKafka = mock(KafkaTemplate.class);
        when(failedKafka.send(anyString(), anyString(), any(Object.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var failedRelay = new OutboxPublisher(jdbc(orders), failedKafka, orders.getBean(ObjectMapper.class),
                orders.getBean(PlatformTransactionManager.class), false);
        assertThrows(IllegalStateException.class, failedRelay::publishPending);
        assertEquals(0, jdbc(orders).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NOT NULL", Integer.class, id));
        assertEquals(payload, jdbc(orders).queryForObject("SELECT payload::text FROM outbox_events WHERE event_id = ?", String.class, eventId));
        await(() -> get(id).getStatus() == OrderStatus.CONFIRMED);
        assertEquals(eventId, jdbc(orders).queryForObject("SELECT event_id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id));
    }
    @Test void inventoryRestartPublishesItsPersistedOutcome() throws Exception {
        UUID id = seed(false, PRODUCT);
        orders.getBean(OutboxPublisher.class).publishPending();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (jdbc(inventory).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id) == 0
                && System.nanoTime() < deadline) Thread.sleep(50);
        UUID eventId = jdbc(inventory).queryForObject("SELECT event_id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id);
        inventory.close();
        inventory = start(InventoryServiceApplication.class, "inventory", inventoryDb,
                "com.lylecommerce.inventory.messaging.OrderCreatedEvent");
        await(() -> get(id).getStatus() == OrderStatus.CONFIRMED);
        assertEquals(eventId, jdbc(inventory).queryForObject("SELECT event_id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id));
    }
    @Test void createOrderCommitsItsEventWithoutWaitingForKafka() throws Exception {
        var command = new CreateOrderCommand(UUID.randomUUID(), List.of(new CreateOrderCommand.Item(PRODUCT, "Fixture", 1, BigDecimal.TEN)));
        var order = orders.getBean(CreateOrderService.class).createOrder(command);
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(1, jdbc(orders).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL", Integer.class, order.getId()));
        var expected = Math.floorMod(order.getId().hashCode(), 10) == 0 ? OrderStatus.CANCELLED : OrderStatus.CONFIRMED;
        await(() -> get(order.getId()).getStatus() == expected);
        if (expected == OrderStatus.CANCELLED) await(() -> jdbc(inventory).queryForObject(
                "SELECT count(*) FROM inventory_reservations WHERE order_id = ? AND released_at IS NOT NULL", Integer.class, order.getId()) == 1);
    }

    @Test void concurrentOrderNotificationsConvergeAndPreserveItemIdentity() throws Exception {
        UUID id = seed(false, PRODUCT);
        UUID itemId = jdbc(orders).queryForObject("SELECT id FROM order_items WHERE order_id = ?", UUID.class, id);
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                boolean payment = i % 2 == 0;
                calls.add(pool.submit(() -> {
                    var workflow = orders.getBean(OrderWorkflowService.class);
                    if (payment) workflow.paymentResult(new PaymentResultEvent(UUID.randomUUID(), id, "SUCCEEDED", Instant.now()));
                    else workflow.inventoryReserved(id);
                }));
            }
            for (var call : calls) call.get(10, TimeUnit.SECONDS);
            assertEquals(OrderStatus.CONFIRMED, get(id).getStatus());
            // Publish the real reservation too: it must be a harmless late notification.
            await(() -> jdbc(payments).queryForObject("SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, id) == 1);
            pump();
            assertEquals(itemId, jdbc(orders).queryForObject("SELECT id FROM order_items WHERE order_id = ?", UUID.class, id));
            assertEquals(1, jdbc(orders).queryForObject("SELECT count(*) FROM order_payment_results WHERE order_id = ?", Integer.class, id));
        } finally { pool.shutdownNow(); }
    }

    @Test void paymentDecisionAndOutboxRollbackTogether() {
        UUID id = UUID.randomUUID();
        var template = new TransactionTemplate(payments.getBean(PlatformTransactionManager.class));
        template.executeWithoutResult(status -> {
            payments.getBean(PaymentProcessor.class).process(id);
            assertEquals(1, jdbc(payments).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id));
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc(payments).queryForObject("SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, id));
        assertEquals(0, jdbc(payments).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id));
    }

    @Test void cancellationAndItsOutboxRollbackTogether() throws Exception {
        UUID id = seed(true, PRODUCT);
        var transaction = new TransactionTemplate(orders.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> {
            orders.getBean(OrderWorkflowService.class).inventoryReserved(id);
            orders.getBean(OrderWorkflowService.class).paymentResult(new PaymentResultEvent(UUID.randomUUID(), id, "FAILED", Instant.now()));
            status.setRollbackOnly();
        });
        assertEquals(OrderStatus.PENDING, get(id).getStatus());
        assertEquals(0, jdbc(orders).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND topic='order-cancelled'", Integer.class, id));
        await(() -> get(id).getStatus() == OrderStatus.CANCELLED && jdbc(inventory).queryForObject(
                "SELECT count(*) FROM inventory_reservations WHERE order_id = ? AND released_at IS NOT NULL", Integer.class, id) == 1);
    }

    @Test void inventoryAndPaymentPublicationFailureKeepOriginalEventIds() throws Exception {
        UUID id = seed(false, PRODUCT);
        orders.getBean(OutboxPublisher.class).publishPending();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (jdbc(inventory).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id) == 0
                && System.nanoTime() < deadline) Thread.sleep(50);
        UUID inventoryEventId = jdbc(inventory).queryForObject("SELECT event_id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id);
        @SuppressWarnings("unchecked") KafkaTemplate<String,Object> failedKafka = mock(KafkaTemplate.class);
        when(failedKafka.send(anyString(), anyString(), any(Object.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var inventoryRelay = new com.lylecommerce.inventory.infrastructure.outbox.OutboxPublisher(jdbc(inventory), failedKafka,
                inventory.getBean(ObjectMapper.class), inventory.getBean(PlatformTransactionManager.class), false);
        assertThrows(IllegalStateException.class, inventoryRelay::publishPending);
        assertEquals(0, jdbc(inventory).queryForObject("SELECT count(*) FROM outbox_events WHERE event_id = ? AND published_at IS NOT NULL", Integer.class, inventoryEventId));
        inventory.getBean(com.lylecommerce.inventory.infrastructure.outbox.OutboxPublisher.class).publishPending();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (jdbc(payments).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id) == 0
                && System.nanoTime() < deadline) Thread.sleep(50);
        UUID paymentEventId = jdbc(payments).queryForObject("SELECT event_id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id);
        var paymentRelay = new com.lylecommerce.payment.infrastructure.outbox.OutboxPublisher(jdbc(payments), failedKafka,
                payments.getBean(ObjectMapper.class), payments.getBean(PlatformTransactionManager.class), false);
        assertThrows(IllegalStateException.class, paymentRelay::publishPending);
        assertEquals(0, jdbc(payments).queryForObject("SELECT count(*) FROM outbox_events WHERE event_id = ? AND published_at IS NOT NULL", Integer.class, paymentEventId));
        await(() -> get(id).getStatus() == OrderStatus.CONFIRMED);
        assertEquals(inventoryEventId, jdbc(inventory).queryForObject("SELECT event_id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id));
        assertEquals(paymentEventId, jdbc(payments).queryForObject("SELECT event_id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id));
    }

    @Test void stockClaimAndOutboxRollbackTogether() {
        UUID id = UUID.randomUUID(); int before = available();
        var template = new TransactionTemplate(inventory.getBean(PlatformTransactionManager.class));
        template.executeWithoutResult(status -> {
            var created = new com.lylecommerce.inventory.messaging.OrderCreatedEvent(UUID.randomUUID(), id,
                    UUID.randomUUID(), BigDecimal.TEN, Instant.now(), List.of(
                    new com.lylecommerce.inventory.messaging.OrderCreatedEvent.Item(PRODUCT, "Fixture", 2, BigDecimal.TEN)));
            assertTrue(inventory.getBean(ReserveInventoryService.class).reserve(created));
            status.setRollbackOnly();
        });
        assertEquals(before, available());
        assertEquals(0, jdbc(inventory).queryForObject("SELECT count(*) FROM inventory_reservations WHERE order_id = ?", Integer.class, id));
        assertEquals(0, jdbc(inventory).queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, id));
    }
    @Test void concurrentOrdersCannotOversell() throws Exception {
        UUID product = UUID.randomUUID();
        jdbc(inventory).update("INSERT INTO inventory_items VALUES (?, 2, 0)", product);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            List<Future<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) calls.add(pool.submit(() -> {
                ready.countDown(); start.await();
                return inventory.getBean(ReserveInventoryService.class).reserve(new com.lylecommerce.inventory.messaging.OrderCreatedEvent(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now(), List.of(
                        new com.lylecommerce.inventory.messaging.OrderCreatedEvent.Item(product, "Limited", 2, BigDecimal.TEN))));
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            for (var call : calls) assertTrue(call.get(10, TimeUnit.SECONDS));
            assertEquals(0, jdbc(inventory).queryForObject("SELECT available_quantity FROM inventory_items WHERE product_id = ?", Integer.class, product));
            assertEquals(2, jdbc(inventory).queryForObject("SELECT reserved_quantity FROM inventory_items WHERE product_id = ?", Integer.class, product));
            // Isolated concurrency probe has no Order records: remove only its unpublished test events.
            jdbc(inventory).update("DELETE FROM outbox_events WHERE aggregate_id IN (SELECT order_id FROM inventory_reservations r WHERE "
                    + "EXISTS (SELECT 1 FROM inventory_reservation_items i WHERE i.order_id=r.order_id AND i.product_id=?) "
                    + "OR rejection_reason = ?)", product, "INSUFFICIENT_STOCK: " + product);
        } finally { pool.shutdownNow(); }
    }
}
