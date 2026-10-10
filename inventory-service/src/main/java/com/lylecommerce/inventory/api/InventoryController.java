package com.lylecommerce.inventory.api;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/inventory")
public class InventoryController {
    public record Stock(UUID productId, int availableQuantity, int reservedQuantity) {}
    private final JdbcTemplate jdbc;
    public InventoryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping public List<Stock> list() {
        return jdbc.query("SELECT product_id, available_quantity, reserved_quantity FROM inventory_items ORDER BY product_id",
                (rs, row) -> new Stock(rs.getObject(1, UUID.class), rs.getInt(2), rs.getInt(3)));
    }
}
