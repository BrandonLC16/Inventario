package com.example.inventory.counts;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID>,
        JpaSpecificationExecutor<InventoryCount> {

    @EntityGraph(attributePaths = "lines")
    @Query("select inventoryCount from InventoryCount inventoryCount "
            + "where inventoryCount.id = :id")
    Optional<InventoryCount> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventoryCount from InventoryCount inventoryCount "
            + "where inventoryCount.id = :id")
    Optional<InventoryCount> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('inventory_counts_folio_seq')",
            nativeQuery = true)
    long nextFolioSequence();
}
