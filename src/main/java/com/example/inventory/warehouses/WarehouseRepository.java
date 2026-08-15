package com.example.inventory.warehouses;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select warehouse from Warehouse warehouse where warehouse.id = :id")
    Optional<Warehouse> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select exists (select 1 from inventory where warehouse_id = :warehouseId and quantity > 0)", nativeQuery = true)
    boolean hasStock(@Param("warehouseId") UUID warehouseId);

    @Query(value = "select exists (select 1 from inventory_reservations where warehouse_id = :warehouseId)", nativeQuery = true)
    boolean hasReservations(@Param("warehouseId") UUID warehouseId);

    @Query(value = "select exists (select 1 from orders where fulfillment_warehouse_id = :warehouseId and status in ('PENDING', 'RESERVED')) or exists (select 1 from purchase_orders where destination_warehouse_id = :warehouseId and status in ('DRAFT', 'ISSUED', 'PARTIALLY_RECEIVED')) or exists (select 1 from inventory_transfers where (source_warehouse_id = :warehouseId or destination_warehouse_id = :warehouseId) and status in ('DRAFT', 'IN_TRANSIT')) or exists (select 1 from inventory_counts where warehouse_id = :warehouseId and status in ('DRAFT', 'OPEN', 'SUBMITTED'))", nativeQuery = true)
    boolean hasOpenOrders(@Param("warehouseId") UUID warehouseId);

    @Query(value = "select setting.product_id from warehouse_product_settings setting join products product on product.id = setting.product_id where setting.warehouse_id = :warehouseId and setting.active = true and product.deleted = false and product.active = true order by setting.product_id limit :maximumResults", nativeQuery = true)
    List<UUID> findProductIdsForPhysicalCount(
            @Param("warehouseId") UUID warehouseId,
            @Param("maximumResults") int maximumResults);

    @Query(value = "select exists (select 1 from warehouse_product_settings where warehouse_id = :warehouseId and product_id = :productId and active = true)", nativeQuery = true)
    boolean isProductActive(@Param("warehouseId") UUID warehouseId,
                            @Param("productId") UUID productId);

    @Query(value = "select exists (select 1 from inventory where warehouse_id = :warehouseId and product_id = :productId and quantity > 0)", nativeQuery = true)
    boolean hasProductStock(@Param("warehouseId") UUID warehouseId,
                            @Param("productId") UUID productId);

    @Query(value = "select exists (select 1 from inventory_reservations where warehouse_id = :warehouseId and product_id = :productId)", nativeQuery = true)
    boolean hasProductReservations(@Param("warehouseId") UUID warehouseId,
                                   @Param("productId") UUID productId);

    @Query(value = "select exists (select 1 from inventory where product_id = :productId and quantity > 0)", nativeQuery = true)
    boolean hasStockForProduct(@Param("productId") UUID productId);

    @Query(value = "select exists (select 1 from inventory_reservations where product_id = :productId)", nativeQuery = true)
    boolean hasReservationsForProduct(@Param("productId") UUID productId);

    @Query(value = """
            select exists (
                select 1 from order_items item
                join orders business_order on business_order.id = item.order_id
                where item.product_id = :productId
                  and business_order.status in ('PENDING', 'RESERVED', 'CONFIRMED')
            ) or exists (
                select 1 from purchase_order_items item
                join purchase_orders purchase_order on purchase_order.id = item.purchase_order_id
                where item.product_id = :productId
                  and purchase_order.status in ('DRAFT', 'ISSUED', 'PARTIALLY_RECEIVED')
            ) or exists (
                select 1 from inventory_transfer_items item
                join inventory_transfers transfer on transfer.id = item.transfer_id
                where item.product_id = :productId
                  and transfer.status in ('DRAFT', 'IN_TRANSIT')
            ) or exists (
                select 1 from inventory_count_lines line
                join inventory_counts inventory_count on inventory_count.id = line.count_id
                where line.product_id = :productId
                  and inventory_count.status in ('DRAFT', 'OPEN', 'SUBMITTED')
            )
            """, nativeQuery = true)
    boolean hasPendingOperationsForProduct(@Param("productId") UUID productId);

    @Modifying
    @Query(value = "insert into warehouse_product_settings (warehouse_id, product_id, minimum_stock, active) select :warehouseId, product.id, 0, true from products product on conflict (warehouse_id, product_id) do nothing", nativeQuery = true)
    void initializeProductSettings(@Param("warehouseId") UUID warehouseId);

    @Modifying
    @Query(value = "insert into warehouse_product_settings (warehouse_id, product_id, minimum_stock, active) select warehouse.id, :productId, 0, true from warehouses warehouse on conflict (warehouse_id, product_id) do nothing", nativeQuery = true)
    void registerProduct(@Param("productId") UUID productId);

    @Modifying
    @Query(value = "insert into warehouse_product_settings (warehouse_id, product_id, minimum_stock, active) values (:warehouseId, :productId, :minimumStock, :active) on conflict (warehouse_id, product_id) do update set minimum_stock = excluded.minimum_stock, active = excluded.active", nativeQuery = true)
    void configureProduct(@Param("warehouseId") UUID warehouseId,
                          @Param("productId") UUID productId,
                          @Param("minimumStock") int minimumStock,
                          @Param("active") boolean active);
}
