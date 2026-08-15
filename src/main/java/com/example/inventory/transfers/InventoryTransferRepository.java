package com.example.inventory.transfers;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InventoryTransferRepository
        extends JpaRepository<InventoryTransfer, UUID>,
        JpaSpecificationExecutor<InventoryTransfer> {

    @EntityGraph(attributePaths = "items")
    @Query("select transfer from InventoryTransfer transfer where transfer.id = :id")
    Optional<InventoryTransfer> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from InventoryTransfer transfer where transfer.id = :id")
    Optional<InventoryTransfer> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('inventory_transfers_folio_seq')",
            nativeQuery = true)
    long nextFolioSequence();
}
