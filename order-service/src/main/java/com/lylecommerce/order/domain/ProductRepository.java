package com.lylecommerce.order.domain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ProductRepository {
    List<Product> findAll();
    Optional<Product> findById(UUID id);
}
