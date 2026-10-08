package com.lylecommerce.inventory.infrastructure.persistence;

import com.lylecommerce.inventory.domain.InventoryItem;
import com.lylecommerce.inventory.domain.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaInventoryRepositoryAdapter implements InventoryRepository {

    private final SpringDataInventoryRepository repository;

    public JpaInventoryRepositoryAdapter(
            SpringDataInventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public InventoryItem save(InventoryItem item) {

        InventoryJpaEntity entity = new InventoryJpaEntity(
                item.getProductId(),
                item.getAvailableQuantity(),
                item.getReservedQuantity()
        );

        repository.save(entity);

        return item;
    }

    @Override
    public Optional<InventoryItem> findByProductId(UUID productId) {

        return repository.findByProductIdForUpdate(productId)
                .map(entity -> new InventoryItem(
                        entity.getProductId(),
                        entity.getAvailableQuantity(),
                        entity.getReservedQuantity()
                ));
    }
}
