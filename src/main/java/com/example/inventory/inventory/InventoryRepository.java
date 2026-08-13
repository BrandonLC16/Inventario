package com.example.inventory.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InventoryRepository extends JpaRepository<InventoryItem, InventoryId> {

    @Query(value = """
            SELECT :warehouseId AS "warehouseId",
                   product.id AS "productId",
                   COALESCE(item.quantity, 0) AS quantity,
                   COALESCE(reservation.reserved_quantity, 0) AS "reservedQuantity",
                   item.updated_at AS "updatedAt"
            FROM products product
            LEFT JOIN inventory item
              ON item.warehouse_id = :warehouseId AND item.product_id = product.id
            LEFT JOIN (
                SELECT product_id, SUM(quantity)::integer AS reserved_quantity
                FROM inventory_reservations
                WHERE warehouse_id = :warehouseId
                GROUP BY product_id
            ) reservation ON reservation.product_id = product.id
            WHERE product.id = :productId
            """, nativeQuery = true)
    Optional<InventoryBalanceProjection> findBalance(
            @Param("warehouseId") UUID warehouseId,
            @Param("productId") UUID productId);

    @Query(value = """
            SELECT :warehouseId AS "warehouseId",
                   product.id AS "productId",
                   COALESCE(item.quantity, 0) AS quantity,
                   COALESCE(reservation.reserved_quantity, 0) AS "reservedQuantity",
                   item.updated_at AS "updatedAt"
            FROM products product
            LEFT JOIN inventory item
              ON item.warehouse_id = :warehouseId AND item.product_id = product.id
            LEFT JOIN (
                SELECT product_id, SUM(quantity)::integer AS reserved_quantity
                FROM inventory_reservations
                WHERE warehouse_id = :warehouseId
                GROUP BY product_id
            ) reservation ON reservation.product_id = product.id
            WHERE product.deleted = false
            ORDER BY product.sku, product.id
            """,
            countQuery = "SELECT count(*) FROM products WHERE deleted = false",
            nativeQuery = true)
    Page<InventoryBalanceProjection> findBalances(
            @Param("warehouseId") UUID warehouseId, Pageable pageable);

    @Modifying
    @Query(value = "insert into inventory (warehouse_id, product_id, quantity, updated_at) values (:warehouseId, :productId, 0, current_timestamp) on conflict (warehouse_id, product_id) do nothing", nativeQuery = true)
    int ensureExists(@Param("warehouseId") UUID warehouseId,
                     @Param("productId") UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryItem item where item.id.warehouseId = :warehouseId and item.id.productId = :productId")
    Optional<InventoryItem> findForUpdate(
            @Param("warehouseId") UUID warehouseId,
            @Param("productId") UUID productId);

    @Query(value = """
            SELECT setting.warehouse_id AS "warehouseId",
                   product.id AS "productId",
                   product.sku AS sku,
                   product.name AS name,
                   COALESCE(item.quantity, 0) AS quantity,
                   COALESCE(reservation.reserved_quantity, 0) AS "reservedQuantity",
                   COALESCE(item.quantity, 0) - COALESCE(reservation.reserved_quantity, 0)
                       AS "availableQuantity",
                   setting.minimum_stock AS "minimumStock"
            FROM warehouse_product_settings setting
            JOIN products product ON product.id = setting.product_id
            LEFT JOIN inventory item
              ON item.warehouse_id = setting.warehouse_id
             AND item.product_id = setting.product_id
            LEFT JOIN (
                SELECT product_id, SUM(quantity)::integer AS reserved_quantity
                FROM inventory_reservations
                WHERE warehouse_id = :warehouseId
                GROUP BY product_id
            ) reservation ON reservation.product_id = product.id
            WHERE setting.warehouse_id = :warehouseId
              AND setting.active = true
              AND product.deleted = false
              AND product.active = true
              AND COALESCE(item.quantity, 0) - COALESCE(reservation.reserved_quantity, 0)
                    <= setting.minimum_stock
              AND (:search IS NULL
                   OR lower(product.sku) LIKE lower(concat('%', :search, '%'))
                   OR lower(product.name) LIKE lower(concat('%', :search, '%')))
              AND (:outOfStockOnly = false
                   OR COALESCE(item.quantity, 0) - COALESCE(reservation.reserved_quantity, 0) = 0)
            ORDER BY product.name, product.id
            """,
            countQuery = """
            SELECT count(*)
            FROM warehouse_product_settings setting
            JOIN products product ON product.id = setting.product_id
            LEFT JOIN inventory item
              ON item.warehouse_id = setting.warehouse_id
             AND item.product_id = setting.product_id
            LEFT JOIN (
                SELECT product_id, SUM(quantity)::integer AS reserved_quantity
                FROM inventory_reservations
                WHERE warehouse_id = :warehouseId
                GROUP BY product_id
            ) reservation ON reservation.product_id = product.id
            WHERE setting.warehouse_id = :warehouseId
              AND setting.active = true
              AND product.deleted = false
              AND product.active = true
              AND COALESCE(item.quantity, 0) - COALESCE(reservation.reserved_quantity, 0)
                    <= setting.minimum_stock
              AND (:search IS NULL
                   OR lower(product.sku) LIKE lower(concat('%', :search, '%'))
                   OR lower(product.name) LIKE lower(concat('%', :search, '%')))
              AND (:outOfStockOnly = false
                   OR COALESCE(item.quantity, 0) - COALESCE(reservation.reserved_quantity, 0) = 0)
            """, nativeQuery = true)
    Page<LowStockProjection> findLowStock(
            @Param("warehouseId") UUID warehouseId,
            @Param("search") String search,
            @Param("outOfStockOnly") boolean outOfStockOnly,
            Pageable pageable);
}
