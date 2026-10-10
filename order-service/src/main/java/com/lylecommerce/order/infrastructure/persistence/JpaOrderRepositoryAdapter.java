package com.lylecommerce.order.infrastructure.persistence;

import com.lylecommerce.order.domain.Order;
import com.lylecommerce.order.domain.OrderRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class JpaOrderRepositoryAdapter
        implements OrderRepository {

    private final SpringDataOrderRepository repository;

    public JpaOrderRepositoryAdapter(
            SpringDataOrderRepository repository) {

        this.repository = repository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = repository.findById(order.getId())
                .orElseGet(() -> OrderPersistenceMapper.toJpaEntity(order));
        entity.updateStatus(order.getStatus(), order.getUpdatedAt());

        OrderJpaEntity savedEntity =
                repository.save(entity);

        return OrderPersistenceMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Order> findByIdForUpdate(UUID orderId) {
        return repository.findByIdForUpdate(orderId).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return repository.findById(orderId)
                .map(OrderPersistenceMapper::toDomain);
    }
}
