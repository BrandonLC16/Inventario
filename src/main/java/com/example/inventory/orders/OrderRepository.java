package com.example.inventory.orders;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<SalesOrder, UUID> {

    @EntityGraph(attributePaths = "items")
    @Query("select salesOrder from SalesOrder salesOrder where salesOrder.id = :id")
    Optional<SalesOrder> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "items")
    List<SalesOrder> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select salesOrder from SalesOrder salesOrder where salesOrder.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") UUID id);
}
