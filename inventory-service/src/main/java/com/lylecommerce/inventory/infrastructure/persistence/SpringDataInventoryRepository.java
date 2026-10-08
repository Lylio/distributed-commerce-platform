package com.lylecommerce.inventory.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataInventoryRepository
        extends JpaRepository<InventoryJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM InventoryJpaEntity i
            WHERE i.productId = :productId
            """)
    Optional<InventoryJpaEntity> findByProductIdForUpdate(
            @Param("productId") UUID productId
    );
}
