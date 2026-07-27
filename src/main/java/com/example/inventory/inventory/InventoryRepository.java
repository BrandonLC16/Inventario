package com.example.inventory.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {

    @Modifying
    @Query(value = "insert into inventory (product_id, quantity, updated_at) values (:productId, 0, current_timestamp) on conflict (product_id) do nothing", nativeQuery = true)
    int ensureExists(@Param("productId") UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryItem item where item.productId = :productId")
    Optional<InventoryItem> findByProductIdForUpdate(@Param("productId") UUID productId);
}
