package com.lylecommerce.inventory.infrastructure.persistence;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/stock")
public class StockController {
    public record Stock(UUID productId, int availableQuantity, int reservedQuantity) {}
    private final SpringDataInventoryRepository repository;
    public StockController(SpringDataInventoryRepository repository) { this.repository = repository; }
    @GetMapping
    public List<Stock> list() {
        return repository.findAll().stream()
            .map(i -> new Stock(i.getProductId(), i.getAvailableQuantity(), i.getReservedQuantity())).toList();
    }
}
