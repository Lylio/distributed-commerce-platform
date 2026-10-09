package com.lylecommerce.inventory.infrastructure.persistence;

import com.lylecommerce.inventory.domain.InventoryReservationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JpaInventoryReservationRepositoryAdapter
        implements InventoryReservationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JpaInventoryReservationRepositoryAdapter(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryClaimOrder(UUID orderId) {

        String sql = """
                INSERT INTO inventory_reservations (order_id)
                VALUES (?)
                ON CONFLICT (order_id) DO NOTHING
                """;

        int rowsInserted = jdbcTemplate.update(sql, orderId);

        return rowsInserted == 1;
    }
}
