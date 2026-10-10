package com.lylecommerce.verification;

import com.lylecommerce.order.OrderServiceApplication;
import com.lylecommerce.inventory.InventoryServiceApplication;
import com.lylecommerce.payment.PaymentServiceApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in browser verification; plain Maven verification remains independent of Node/browser installation. */
@Testcontainers
@EnabledIfSystemProperty(named = "commerce.browser-tests", matches = "true")
class BrowserJourneyIntegrationTest {
    @Container static final PostgreSQLContainer<?> orderDb = new PostgreSQLContainer<>("postgres:17");
    @Container static final PostgreSQLContainer<?> inventoryDb = new PostgreSQLContainer<>("postgres:17");
    @Container static final PostgreSQLContainer<?> paymentDb = new PostgreSQLContainer<>("postgres:17");
    @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.0");

    @Test void shoppingJourneyAgainstRealServices() throws Exception {
        try (var orders = start(OrderServiceApplication.class, "order", orderDb,
                    "com.lylecommerce.order.infrastructure.messaging.InventoryReservedEvent");
             var inventory = start(InventoryServiceApplication.class, "inventory", inventoryDb,
                    "com.lylecommerce.inventory.messaging.OrderCreatedEvent");
             var payments = start(PaymentServiceApplication.class, "payment", paymentDb,
                    "com.lylecommerce.payment.messaging.InventoryReservedEvent")) {
            var process = new ProcessBuilder("npm", "run", "test:e2e").directory(Path.of("../frontend").toFile()).redirectErrorStream(true);
            process.environment().put("ORDER_API_TARGET", url(orders));
            process.environment().put("INVENTORY_API_TARGET", url(inventory));
            // Independent of a developer's running frontend; Playwright owns and stops its Vite server.
            process.environment().put("FRONTEND_PORT", "5179");
            var child = process.start();
            // Route child output through Java's System.out so Surefire's fork protocol stays intact.
            var output = Thread.startVirtualThread(() -> {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream()))) {
                    reader.lines().forEach(System.out::println);
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
            try {
                assertTrue(child.waitFor(180, TimeUnit.SECONDS), "Browser journey exceeded three minutes");
                output.join(10000);
                assertEquals(0, child.exitValue(), "Playwright shopping journey failed");
            } finally { if (child.isAlive()) { child.descendants().forEach(ProcessHandle::destroy); child.destroyForcibly(); } }
        }
    }
    private String url(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }
    private ConfigurableApplicationContext start(Class<?> app, String service, PostgreSQLContainer<?> db, String input) {
        return new SpringApplicationBuilder(app).web(WebApplicationType.SERVLET).run(
                "--spring.application.name=" + service + "-service", "--server.port=0", "--spring.profiles.active=dev",
                "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(),
                "--spring.flyway.locations=filesystem:" + Path.of("../" + service + "-service/src/main/resources/db/migration").toAbsolutePath(),
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
                "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "--spring.kafka.consumer.group-id=" + service + "-service", "--spring.kafka.consumer.auto-offset-reset=earliest",
                "--spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "--spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JacksonJsonDeserializer",
                "--spring.kafka.consumer.properties.spring.json.value.default.type=" + input,
                "--spring.kafka.consumer.properties.spring.json.use.type.headers=false",
                "--spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "--spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
                "--outbox.scheduling-enabled=true", "--outbox.poll-interval-ms=100",
                "--payment.simulation.outcome=SUCCEEDED", "--logging.level.root=WARN");
    }
}
