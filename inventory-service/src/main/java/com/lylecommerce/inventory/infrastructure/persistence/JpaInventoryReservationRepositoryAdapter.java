package com.lylecommerce.inventory.infrastructure.persistence;

import com.lylecommerce.inventory.domain.InventoryReservationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaInventoryReservationRepositoryAdapter implements InventoryReservationRepository {
    private final JdbcTemplate jdbc;
    public JpaInventoryReservationRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean tryClaimOrder(UUID orderId) {
        return jdbc.update("INSERT INTO inventory_reservations(order_id) VALUES (?) ON CONFLICT(order_id) DO NOTHING", orderId) == 1;
    }

    @Override
    public void markRejected(UUID orderId, String reason) {
        jdbc.update("UPDATE inventory_reservations SET outcome = 'REJECTED', rejection_reason = ? WHERE order_id = ?", reason, orderId);
    }

    @Override
    public void recordItems(UUID orderId, List<ReservedItem> items) {
        for (ReservedItem item : items) {
            jdbc.update("INSERT INTO inventory_reservation_items(order_id,product_id,quantity) VALUES (?,?,?)",
                    orderId, item.productId(), item.quantity());
        }
    }

    @Override
    public List<ReservedItem> lockActiveItems(UUID orderId) {
        List<Boolean> active = jdbc.query("SELECT released_at IS NULL FROM inventory_reservations WHERE order_id = ? AND outcome = 'RESERVED' FOR UPDATE",
                (rs, row) -> rs.getBoolean(1), orderId);
        if (active.isEmpty() || !active.get(0)) return null;
        return jdbc.query("SELECT product_id, quantity FROM inventory_reservation_items WHERE order_id = ? ORDER BY product_id",
                (rs, row) -> new ReservedItem(rs.getObject(1, UUID.class), rs.getInt(2)), orderId);
    }

    @Override
    public void markReleased(UUID orderId) {
        if (jdbc.update("UPDATE inventory_reservations SET released_at = CURRENT_TIMESTAMP WHERE order_id = ? AND released_at IS NULL", orderId) != 1) {
            throw new IllegalStateException("Reservation already released or missing: " + orderId);
        }
    }
}
