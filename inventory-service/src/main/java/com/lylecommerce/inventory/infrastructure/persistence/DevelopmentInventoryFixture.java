package com.lylecommerce.inventory.infrastructure.persistence;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
@Profile("dev")
public class DevelopmentInventoryFixture implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public DevelopmentInventoryFixture(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void run(ApplicationArguments args) {
        seed("22222222-2222-2222-2222-222222222222", 100);
        seed("33333333-3333-3333-3333-333333333333", 0);
        seed("44444444-4444-4444-4444-444444444444", 48);
        seed("55555555-5555-5555-5555-555555555555", 24);
        seed("66666666-6666-6666-6666-666666666666", 32);
        seed("77777777-7777-7777-7777-777777777777", 64);
    }
    private void seed(String productId, int quantity) {
        jdbc.update("INSERT INTO inventory_items(product_id, available_quantity, reserved_quantity) VALUES (?, ?, 0) "
                + "ON CONFLICT(product_id) DO NOTHING", UUID.fromString(productId), quantity);
    }
}
