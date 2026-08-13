package com.example.inventory;

import com.example.inventory.users.RoleName;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryReservationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "reservation-password-123";

    private String adminToken;
    private String salesToken;

    @BeforeEach
    void authenticateActors() throws Exception {
        createUser("reservation-admin", PASSWORD, true, false, RoleName.ADMIN);
        createUser("reservation-sales", PASSWORD, true, false, RoleName.SALES);
        adminToken = login("reservation-admin", PASSWORD);
        salesToken = login("reservation-sales", PASSWORD);
    }

    @Test
    void simultaneousReservationsPreventOversellingAndManualConsumption()
            throws Exception {
        UUID productId = createProduct("RESERVE-RACE", 5);
        UUID firstOrder = createOrder(new Item(productId, 4));
        UUID secondOrder = createOrder(new Item(productId, 4));

        List<Integer> statuses = concurrently(
                "/api/v1/orders/" + firstOrder + "/reserve",
                "/api/v1/orders/" + secondOrder + "/reserve");

        assertEquals(List.of(200, 400), statuses);
        assertBalance(productId, 5, 4, 1);
        assertEquals(1, reservationCount(productId));
        assertEquals(1, movementCount(productId, "ORDER_RESERVED"));
        assertEquals(List.of("PENDING", "RESERVED"), jdbcTemplate.queryForList(
                "SELECT status FROM orders ORDER BY status", String.class));

        authenticated(patch("/api/v1/inventory/{id}/adjustments", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":-2}
                                """), adminToken)
                .andExpect(status().isBadRequest());
        assertBalance(productId, 5, 4, 1);

        UUID reservedOrder = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE status = 'RESERVED'", UUID.class);
        authenticated(post("/api/v1/orders/{id}/reserve", reservedOrder), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        assertEquals(1, reservationCount(productId));
        assertEquals(1, movementCount(productId, "ORDER_RESERVED"));
    }

    @Test
    void multiProductReservationRollsBackCompletelyWhenOneItemLacksStock()
            throws Exception {
        UUID firstProduct = createProduct("RESERVE-ROLLBACK-A", 5);
        UUID secondProduct = createProduct("RESERVE-ROLLBACK-B", 1);
        UUID orderId = createOrder(
                new Item(firstProduct, 3), new Item(secondProduct, 2));

        authenticated(post("/api/v1/orders/{id}/reserve", orderId), salesToken)
                .andExpect(status().isBadRequest());

        assertBalance(firstProduct, 5, 0, 5);
        assertBalance(secondProduct, 1, 0, 1);
        assertEquals(0, reservationCount(firstProduct));
        assertEquals(0, reservationCount(secondProduct));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM stock_movements
                WHERE business_reference = ? AND movement_type = 'ORDER_RESERVED'
                """, Integer.class, orderId.toString()));
        assertEquals("PENDING", orderStatus(orderId));
    }

    @Test
    void editingReservedOrderReleasesOldItemsAndReturnsItToPending()
            throws Exception {
        UUID oldProduct = createProduct("RESERVE-EDIT-OLD", 6);
        UUID newProduct = createProduct("RESERVE-EDIT-NEW", 8);
        UUID orderId = createOrder(new Item(oldProduct, 4));
        reserve(orderId);
        assertBalance(oldProduct, 6, 4, 2);

        authenticated(put("/api/v1/orders/{id}/items", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":3}]}
                                """.formatted(newProduct)), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reservedAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].productId")
                        .value(newProduct.toString()));

        assertBalance(oldProduct, 6, 0, 6);
        assertBalance(newProduct, 8, 0, 8);
        assertEquals(0, reservationCount(oldProduct));
        assertEquals(1, movementCount(oldProduct, "ORDER_RESERVED"));
        assertEquals(1, movementCount(
                oldProduct, "ORDER_RESERVATION_RELEASED"));

        reserve(orderId);
        assertBalance(newProduct, 8, 3, 5);
    }

    @Test
    void explicitReleaseIsIdempotentAndReservedDeletionAlsoReleases()
            throws Exception {
        UUID productId = createProduct("RESERVE-RELEASE", 10);
        UUID releasedOrder = createOrder(new Item(productId, 4));
        reserve(releasedOrder);

        authenticated(post("/api/v1/orders/{id}/release", releasedOrder), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        authenticated(post("/api/v1/orders/{id}/release", releasedOrder), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertBalance(productId, 10, 0, 10);
        assertEquals(1, movementCount(
                productId, "ORDER_RESERVATION_RELEASED"));

        UUID deletedOrder = createOrder(new Item(productId, 6));
        reserve(deletedOrder);
        authenticated(delete("/api/v1/orders/{id}", deletedOrder), salesToken)
                .andExpect(status().isNoContent());

        assertBalance(productId, 10, 0, 10);
        assertEquals(0, reservationCount(productId));
        assertEquals(2, movementCount(
                productId, "ORDER_RESERVATION_RELEASED"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE id = ?",
                Integer.class, deletedOrder));
    }

    @Test
    void confirmationConsumesReservationOnceAndCancellationRestoresPhysicalStock()
            throws Exception {
        UUID productId = createProduct("RESERVE-CONFIRM", 10);
        UUID orderId = createOrder(new Item(productId, 4));
        reserve(orderId);

        assertBalance(productId, 10, 4, 6);
        authenticated(post("/api/v1/orders/{id}/confirm", orderId), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        authenticated(post("/api/v1/orders/{id}/confirm", orderId), salesToken)
                .andExpect(status().isOk());

        assertBalance(productId, 6, 0, 6);
        assertEquals(0, reservationCount(productId));
        assertEquals(1, movementCount(productId, "ORDER_CONFIRMED"));
        var confirmedMovement = jdbcTemplate.queryForMap("""
                SELECT quantity_delta, reservation_delta,
                       reserved_before, reserved_after
                FROM stock_movements
                WHERE product_id = ? AND movement_type = 'ORDER_CONFIRMED'
                """, productId);
        assertEquals(-4, number(confirmedMovement.get("quantity_delta")));
        assertEquals(-4, number(confirmedMovement.get("reservation_delta")));
        assertEquals(4, number(confirmedMovement.get("reserved_before")));
        assertEquals(0, number(confirmedMovement.get("reserved_after")));

        authenticated(post("/api/v1/orders/{id}/cancel", orderId), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        authenticated(post("/api/v1/orders/{id}/cancel", orderId), salesToken)
                .andExpect(status().isOk());

        assertBalance(productId, 10, 0, 10);
        assertEquals(1, movementCount(productId, "ORDER_CANCELLED"));
    }

    @Test
    void lowStockUsesAvailableQuantityWhilePhysicalStockRemainsReserved()
            throws Exception {
        UUID productId = createProduct("RESERVE-AVAILABLE", 8, 5);
        UUID orderId = createOrder(new Item(productId, 4));
        reserve(orderId);

        authenticated(get("/api/v1/inventory/low-stock")
                        .param("search", "RESERVE-AVAILABLE"), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].quantity").value(8))
                .andExpect(jsonPath("$.content[0].reservedQuantity").value(4))
                .andExpect(jsonPath("$.content[0].availableQuantity").value(4))
                .andExpect(jsonPath("$.content[0].replenishmentQuantity").value(1))
                .andExpect(jsonPath("$.content[0].alert").value("LOW_STOCK"));
    }

    @Test
    void v10AssignsAllV9HistoricalDataToDeterministicMainWarehouse() {
        String schema = "warehouse_v10_upgrade";
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA " + schema);
        Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .target("9")
                .load()
                .migrate();

        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO warehouse_v10_upgrade.products (
                    id, sku, name, price, active, created_at, updated_at,
                    deleted, minimum_stock
                ) VALUES (?, 'LEGACY-WAREHOUSE', 'Legacy', 10, true,
                          current_timestamp, current_timestamp, false, 7)
                """, productId);
        jdbcTemplate.update("""
                INSERT INTO warehouse_v10_upgrade.inventory (
                    product_id, quantity, updated_at
                ) VALUES (?, 5, current_timestamp)
                """, productId);
        jdbcTemplate.update("""
                INSERT INTO warehouse_v10_upgrade.orders (
                    id, status, created_at, updated_at, folio, currency,
                    total, created_by, reserved_at, reserved_by
                ) VALUES (?, 'RESERVED', current_timestamp, current_timestamp,
                          'ORD-LEGACY-WAREHOUSE', 'MXN', 20, 'legacy-user',
                          current_timestamp, 'legacy-user')
                """, orderId);
        jdbcTemplate.update("""
                INSERT INTO warehouse_v10_upgrade.inventory_reservations (
                    id, order_id, product_id, quantity, reserved_at, reserved_by
                ) VALUES (?, ?, ?, 2, current_timestamp, 'legacy-user')
                """, reservationId, orderId, productId);
        jdbcTemplate.update("""
                INSERT INTO warehouse_v10_upgrade.stock_movements (
                    id, product_id, movement_type, quantity_delta,
                    balance_before, balance_after, business_reference,
                    occurred_at, responsible_user, reservation_delta,
                    reserved_before, reserved_after
                ) VALUES (?, ?, 'INITIAL_STOCK', 5, 0, 5, 'LEGACY-STOCK',
                          current_timestamp, 'legacy-user', 0, 0, 0)
                """, movementId, productId);

        Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();

        UUID main = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(main, jdbcTemplate.queryForObject("""
                SELECT id FROM warehouse_v10_upgrade.warehouses WHERE code = 'MAIN'
                """, UUID.class));
        assertEquals(main, jdbcTemplate.queryForObject("""
                SELECT warehouse_id FROM warehouse_v10_upgrade.inventory
                WHERE product_id = ?
                """, UUID.class, productId));
        assertEquals(main, jdbcTemplate.queryForObject("""
                SELECT warehouse_id FROM warehouse_v10_upgrade.inventory_reservations
                WHERE id = ?
                """, UUID.class, reservationId));
        assertEquals(main, jdbcTemplate.queryForObject("""
                SELECT warehouse_id FROM warehouse_v10_upgrade.stock_movements
                WHERE id = ?
                """, UUID.class, movementId));
        assertEquals(main, jdbcTemplate.queryForObject("""
                SELECT fulfillment_warehouse_id FROM warehouse_v10_upgrade.orders
                WHERE id = ?
                """, UUID.class, orderId));
        assertEquals(7, jdbcTemplate.queryForObject("""
                SELECT minimum_stock FROM warehouse_v10_upgrade.warehouse_product_settings
                WHERE warehouse_id = ? AND product_id = ?
                """, Integer.class, main, productId));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'warehouse_v10_upgrade'
                  AND table_name = 'products' AND column_name = 'minimum_stock'
                """, Integer.class));
    }
    private UUID createProduct(String sku, int stock) throws Exception {
        return createProduct(sku, stock, 0);
    }

    private UUID createProduct(String sku, int stock, int minimumStock)
            throws Exception {
        String location = authenticated(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":10,
                                 "active":true,"minimumStock":%d}
                                """.formatted(sku, sku, minimumStock)), adminToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        UUID productId = idFromLocation(location);
        if (stock > 0) {
            authenticated(patch("/api/v1/inventory/{id}/adjustments", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantityDelta":%d}
                                    """.formatted(stock)), adminToken)
                    .andExpect(status().isOk());
        }
        return productId;
    }

    private UUID createOrder(Item... items) throws Exception {
        String content = Arrays.stream(items)
                .map(item -> """
                        {"productId":"%s","quantity":%d}
                        """.formatted(item.productId(), item.quantity()).trim())
                .collect(Collectors.joining(","));
        String location = authenticated(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[%s]}
                                """.formatted(content)), salesToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private void reserve(UUID orderId) throws Exception {
        authenticated(post("/api/v1/orders/{id}/reserve", orderId), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    private void assertBalance(UUID productId, int physical, int reserved,
                               int available) throws Exception {
        authenticated(get("/api/v1/inventory/{id}", productId), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(physical))
                .andExpect(jsonPath("$.reservedQuantity").value(reserved))
                .andExpect(jsonPath("$.availableQuantity").value(available));
        assertEquals(physical, jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory WHERE product_id = ?",
                Integer.class, productId));
    }

    private int reservationCount(UUID productId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM inventory_reservations
                WHERE product_id = ?
                """, Integer.class, productId);
    }

    private int movementCount(UUID productId, String type) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM stock_movements
                WHERE product_id = ? AND movement_type = ?
                """, Integer.class, productId, type);
    }

    private String orderStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class, orderId);
    }

    private ResultActions authenticated(MockHttpServletRequestBuilder request,
                                        String token) throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + token));
    }

    private UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private int number(Object value) {
        return ((Number) value).intValue();
    }

    private List<Integer> concurrently(String firstPath, String secondPath)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Integer> first = executor.submit(
                    concurrentRequest(firstPath, barrier));
            Future<Integer> second = executor.submit(
                    concurrentRequest(secondPath, barrier));
            return List.of(
                            first.get(30, TimeUnit.SECONDS),
                            second.get(30, TimeUnit.SECONDS))
                    .stream().sorted().toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> concurrentRequest(String path, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post(path)
                            .header(AUTHORIZATION, "Bearer " + salesToken))
                    .andReturn().getResponse().getStatus();
        };
    }

    private record Item(UUID productId, int quantity) {
    }
}
