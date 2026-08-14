package com.example.inventory.suppliers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface SupplierProductRepository
        extends JpaRepository<SupplierProduct, SupplierProductId> {

    Page<SupplierProduct> findBySupplierId(UUID supplierId, Pageable pageable);

    boolean existsByProductIdAndPreferredTrueAndSupplierIdNot(
            UUID productId, UUID supplierId);

    @Modifying
    @Query("update SupplierProduct supplierProduct set supplierProduct.preferred = false "
            + "where supplierProduct.supplierId = :supplierId "
            + "and supplierProduct.preferred = true")
    void clearPreferredBySupplierId(@Param("supplierId") UUID supplierId);
}
