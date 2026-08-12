package com.example.inventory.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {

    @Modifying
    @Query(value = "insert into inventory (product_id, quantity, updated_at) values (:productId, 0, current_timestamp) on conflict (product_id) do nothing", nativeQuery = true)
    int ensureExists(@Param("productId") UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryItem item where item.productId = :productId")
    Optional<InventoryItem> findByProductIdForUpdate(@Param("productId") UUID productId);

    @Query(value = """
            SELECT product.id AS "productId",
                   product.sku AS sku,
                   product.name AS name,
                   COALESCE(item.quantity, 0) AS quantity,
                   product.minimum_stock AS "minimumStock"
            FROM products product
            LEFT JOIN inventory item ON item.product_id = product.id
            WHERE product.deleted = false
              AND product.active = true
              AND COALESCE(item.quantity, 0) <= product.minimum_stock
              AND (:search IS NULL
                   OR lower(product.sku) LIKE lower(concat('%', :search, '%'))
                   OR lower(product.name) LIKE lower(concat('%', :search, '%')))
              AND (:outOfStockOnly = false OR COALESCE(item.quantity, 0) = 0)
            ORDER BY product.name, product.id
            """,
            countQuery = """
            SELECT count(*)
            FROM products product
            LEFT JOIN inventory item ON item.product_id = product.id
            WHERE product.deleted = false
              AND product.active = true
              AND COALESCE(item.quantity, 0) <= product.minimum_stock
              AND (:search IS NULL
                   OR lower(product.sku) LIKE lower(concat('%', :search, '%'))
                   OR lower(product.name) LIKE lower(concat('%', :search, '%')))
              AND (:outOfStockOnly = false OR COALESCE(item.quantity, 0) = 0)
            """,
            nativeQuery = true)
    Page<LowStockProjection> findLowStock(
            @Param("search") String search,
            @Param("outOfStockOnly") boolean outOfStockOnly,
            Pageable pageable);
}
