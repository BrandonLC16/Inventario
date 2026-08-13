package com.example.inventory.warehouses;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(value = "select exists (select 1 from orders where fulfillment_warehouse_id = :warehouseId and status in ('PENDING', 'RESERVED'))", nativeQuery = true)
    boolean hasOpenOrders(@Param("warehouseId") UUID warehouseId);

    @Query(value = "select exists (select 1 from warehouse_product_settings where warehouse_id = :warehouseId and product_id = :productId and active = true)", nativeQuery = true)
    boolean isProductActive(@Param("warehouseId") UUID warehouseId,
                            @Param("productId") UUID productId);

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
