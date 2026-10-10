package com.lylecommerce.order.infrastructure.persistence;
import com.lylecommerce.order.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class JdbcProductRepositoryAdapter implements ProductRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<Product> mapper = (rs, row) -> new Product(rs.getObject("id", UUID.class),
            rs.getString("name"), rs.getString("subtitle"), rs.getString("category"), rs.getString("description"),
            List.of(rs.getString("features").split("\\|")), rs.getString("illustration"), rs.getString("color"),
            rs.getBigDecimal("unit_price"), rs.getString("currency"));
    public JdbcProductRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Product> findAll() { return jdbc.query("SELECT * FROM products WHERE active ORDER BY id", mapper); }
    public Optional<Product> findById(UUID id) {
        return jdbc.query("SELECT * FROM products WHERE id = ? AND active", mapper, id).stream().findFirst();
    }
}
