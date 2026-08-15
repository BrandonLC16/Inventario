package com.example.inventory.counts;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InventoryCountLineRepository
        extends JpaRepository<InventoryCountLine, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select line from InventoryCountLine line "
            + "where line.inventoryCount.id = :countId order by line.productId")
    List<InventoryCountLine> findByCountIdForUpdate(
            @Param("countId") UUID countId);

    @Query("select count(line) from InventoryCountLine line "
            + "where line.inventoryCount.id = :countId")
    long countByCountId(@Param("countId") UUID countId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select line from InventoryCountLine line "
            + "where line.inventoryCount.id = :countId "
            + "and line.productId = :productId")
    Optional<InventoryCountLine> findByCountIdAndProductIdForUpdate(
            @Param("countId") UUID countId,
            @Param("productId") UUID productId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM inventory_count_lines line
                JOIN inventory_counts inventory_count
                  ON inventory_count.id = line.count_id
                WHERE inventory_count.warehouse_id = :warehouseId
                  AND inventory_count.status IN ('DRAFT', 'OPEN', 'SUBMITTED')
                  AND line.product_id IN (:productIds)
            )
            """, nativeQuery = true)
    boolean existsActiveOverlap(
            @Param("warehouseId") UUID warehouseId,
            @Param("productIds") Collection<UUID> productIds);
}
