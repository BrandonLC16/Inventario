package com.example.inventory.purchases;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, UUID> {

    Optional<PurchaseReceipt> findByPurchaseOrderIdAndExternalReference(
            UUID purchaseOrderId, String externalReference);

    boolean existsByPurchaseOrderId(UUID purchaseOrderId);

    List<PurchaseReceipt> findByPurchaseOrderIdOrderByReceivedAtAscIdAsc(
            UUID purchaseOrderId);

    @Query(value = "select nextval('purchase_receipts_folio_seq')", nativeQuery = true)
    long nextFolioSequence();
}
