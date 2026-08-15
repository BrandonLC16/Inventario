package com.example.inventory;

import com.example.inventory.users.RoleName;
import com.jayway.jsonpath.JsonPath;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryCountIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "counts-password-123";

    private String managerToken;
    private String salesToken;

    @BeforeEach
    void authenticateActors() throws Exception {
        createUser("count-manager", PASSWORD, true, false,
                RoleName.INVENTORY_MANAGER);
        createUser("count-sales", PASSWORD, true, false, RoleName.SALES);
        managerToken = login("count-manager", PASSWORD);
        salesToken = login("count-sales", PASSWORD);
    }

    @Test
    void nonBlockingCountPreservesLaterMovementsAndPostingIsIdempotent()
            throws Exception {
        UUID warehouse = createWarehouse("COUNT-LIFE-WAREHOUSE");
        UUID adjustedProduct = createProduct("COUNT-LIFE-A");
        UUID unchangedProduct = createProduct("COUNT-LIFE-B");
        adjust(warehouse, adjustedProduct, 10);
        adjust(warehouse, unchangedProduct, 5);

        String created = createSelectedCount(
                warehouse, adjustedProduct, unchangedProduct);
        UUID countId = countId(created);
        String folio = JsonPath.read(created, "$.folio");
        assertNull(lineExpected(countId, adjustedProduct));
        performManager(delete("/api/v1/warehouses/{id}", warehouse))
                .andExpect(status().isConflict());

        performManager(post("/api/v1/inventory-counts/{id}/open", countId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openedAt").isNotEmpty());
        assertEquals(10, lineExpected(countId, adjustedProduct));
        assertEquals(5, lineExpected(countId, unchangedProduct));

        adjust(warehouse, adjustedProduct, 3);
        capture(countId, adjustedProduct, 12, "first pass")
                .andExpect(status().isOk());
        assertEquals(13, lineExpected(countId, adjustedProduct));
        assertEquals(-1, lineVariance(countId, adjustedProduct));
        assertEquals(13, stock(warehouse, adjustedProduct));

        adjust(warehouse, adjustedProduct, 2);
        capture(countId, adjustedProduct, 14, "corrected pass")
                .andExpect(status().isOk());
        capture(countId, unchangedProduct, 5, null)
                .andExpect(status().isOk());
        assertEquals(15, lineExpected(countId, adjustedProduct));
        assertEquals(-1, lineVariance(countId, adjustedProduct));
        assertEquals(0, lineVariance(countId, unchangedProduct));
        assertEquals(15, stock(warehouse, adjustedProduct));

        performManager(post("/api/v1/inventory-counts/{id}/submit", countId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
        capture(countId, adjustedProduct, 14, null)
                .andExpect(status().isConflict());
        adjust(warehouse, adjustedProduct, 2);
        assertEquals(17, stock(warehouse, adjustedProduct));

        performManager(post("/api/v1/inventory-counts/{id}/post", countId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.postedAt").isNotEmpty());
        performManager(post("/api/v1/inventory-counts/{id}/post", countId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        assertEquals(16, stock(warehouse, adjustedProduct));
        assertEquals(5, stock(warehouse, unchangedProduct));
        assertEquals(1, physicalMovementCount(countId));
        assertEquals(-1, physicalMovementDelta(countId, adjustedProduct));
        performManager(post("/api/v1/inventory-counts/{id}/cancel", countId))
                .andExpect(status().isConflict());
        performManager(get("/api/v1/inventory-counts")
                        .param("status", "POSTED")
                        .param("scope", "SELECTED")
                        .param("warehouseId", warehouse.toString())
                        .param("folio", folio.substring(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id")
                        .value(countId.toString()));
    }

    @Test
    void postingBelowReservedStockIsRejectedUntilReservationsAreReleased()
            throws Exception {
        UUID warehouse = createWarehouse("COUNT-RESERVED-WAREHOUSE");
        UUID product = createProduct("COUNT-RESERVED-PRODUCT");
        adjust(warehouse, product, 10);
        UUID orderId = createSalesOrder(warehouse, product, 7);
        performSales(post("/api/v1/orders/{id}/reserve", orderId))
                .andExpect(status().isOk());
        UUID countId = countId(createSelectedCount(warehouse, product));
        openAndCapture(countId, product, 6);
        performManager(post("/api/v1/inventory-counts/{id}/submit", countId))
                .andExpect(status().isOk());

        performManager(post("/api/v1/inventory-counts/{id}/post", countId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Physical count result cannot be below reserved inventory"));
        assertEquals(10, stock(warehouse, product));
        assertEquals(7, reserved(warehouse, product));
        assertEquals("SUBMITTED", countStatus(countId));
        assertEquals(0, physicalMovementCount(countId));

        performSales(post("/api/v1/orders/{id}/release", orderId))
                .andExpect(status().isOk());
        performManager(post("/api/v1/inventory-counts/{id}/post", countId))
                .andExpect(status().isOk());
        assertEquals(6, stock(warehouse, product));
        assertEquals(0, reserved(warehouse, product));
    }

    @Test
    void multiProductPostingRollsBackEveryAdjustmentWhenOneConflicts()
            throws Exception {
        UUID warehouse = createWarehouse("COUNT-ROLLBACK-WAREHOUSE");
        UUID productA = createProduct("COUNT-ROLLBACK-A");
        UUID productB = createProduct("COUNT-ROLLBACK-B");
        UUID firstProduct = productA.compareTo(productB) < 0
                ? productA : productB;
        UUID reservedProduct = firstProduct.equals(productA)
                ? productB : productA;
        adjust(warehouse, firstProduct, 10);
        adjust(warehouse, reservedProduct, 10);
        UUID orderId = createSalesOrder(warehouse, reservedProduct, 7);
        performSales(post("/api/v1/orders/{id}/reserve", orderId))
                .andExpect(status().isOk());
        UUID countId = countId(createSelectedCount(
                warehouse, firstProduct, reservedProduct));
        performManager(post("/api/v1/inventory-counts/{id}/open", countId))
                .andExpect(status().isOk());
        capture(countId, firstProduct, 9, null).andExpect(status().isOk());
        capture(countId, reservedProduct, 6, null).andExpect(status().isOk());
        performManager(post("/api/v1/inventory-counts/{id}/submit", countId))
                .andExpect(status().isOk());

        performManager(post("/api/v1/inventory-counts/{id}/post", countId))
                .andExpect(status().isConflict());

        assertEquals(10, stock(warehouse, firstProduct));
        assertEquals(10, stock(warehouse, reservedProduct));
        assertEquals(0, physicalMovementCount(countId));
        assertEquals("SUBMITTED", countStatus(countId));
    }

    @Test
    void overlappingCountsAreRejectedEvenWhenCreatedConcurrently()
            throws Exception {
        UUID warehouse = createWarehouse("COUNT-OVERLAP-WAREHOUSE");
        UUID product = createProduct("COUNT-OVERLAP-PRODUCT");

        assertEquals(List.of(201, 409),
                createCountsConcurrently(warehouse, product));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM inventory_counts inventory_count
                JOIN inventory_count_lines line
                  ON line.count_id = inventory_count.id
                WHERE inventory_count.warehouse_id = ?
                  AND line.product_id = ?
                  AND inventory_count.status IN ('DRAFT', 'OPEN', 'SUBMITTED')
                """, Integer.class, warehouse, product));
    }

    @Test
    void fullScopeIncludesWarehouseProductsAndCancellationReleasesThem()
            throws Exception {
        UUID warehouse = createWarehouse("COUNT-FULL-WAREHOUSE");
        UUID productA = createProduct("COUNT-FULL-A");
        UUID productB = createProduct("COUNT-FULL-B");
        UUID selectedId = countId(createSelectedCount(warehouse, productA));

        performManager(post("/api/v1/inventory-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullCountJson(warehouse)))
                .andExpect(status().isConflict());
        performManager(post("/api/v1/inventory-counts/{id}/cancel", selectedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        performManager(post("/api/v1/inventory-counts/{id}/cancel", selectedId))
                .andExpect(status().isOk());

        String full = performManager(post("/api/v1/inventory-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullCountJson(warehouse)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("FULL"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        UUID fullId = countId(full);
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_count_lines WHERE count_id = ?",
                Integer.class, fullId));
        performManager(post("/api/v1/inventory-counts/{id}/open", fullId))
                .andExpect(status().isOk());
        performManager(post("/api/v1/inventory-counts/{id}/cancel", fullId))
                .andExpect(status().isOk());
        createSelectedCount(warehouse, productA, productB);

        performManager(post("/api/v1/inventory-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":"%s","scope":"FULL",
                                 "productIds":["%s"]}
                                """.formatted(warehouse, productA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capturesMustBeNonNegativeAndAllLinesAreRequiredBeforeSubmit()
            throws Exception {
        UUID warehouse = createWarehouse("COUNT-VALIDATE-WAREHOUSE");
        UUID firstProduct = createProduct("COUNT-VALIDATE-A");
        UUID secondProduct = createProduct("COUNT-VALIDATE-B");
        UUID outsideProduct = createProduct("COUNT-VALIDATE-OUTSIDE");
        UUID countId = countId(createSelectedCount(
                warehouse, firstProduct, secondProduct));
        performManager(post("/api/v1/inventory-counts/{id}/open", countId))
                .andExpect(status().isOk());

        capture(countId, firstProduct, -1, null)
                .andExpect(status().isBadRequest());
        capture(countId, outsideProduct, 0, null)
                .andExpect(status().isNotFound());
        capture(countId, firstProduct, 0, null)
                .andExpect(status().isOk());
        performManager(post("/api/v1/inventory-counts/{id}/submit", countId))
                .andExpect(status().isConflict());
        assertEquals("OPEN", countStatus(countId));

        mockMvc.perform(get("/api/v1/inventory-counts")
                        .header(AUTHORIZATION, "Bearer " + salesToken))
                .andExpect(status().isForbidden());
    }

    private void openAndCapture(UUID countId, UUID product, int quantity)
            throws Exception {
        performManager(post("/api/v1/inventory-counts/{id}/open", countId))
                .andExpect(status().isOk());
        capture(countId, product, quantity, null)
                .andExpect(status().isOk());
    }

    private ResultActions capture(UUID countId, UUID product, int quantity,
                                  String notes) throws Exception {
        String notesProperty = notes == null ? ""
                : ",\"notes\":\"" + notes + "\"";
        return performManager(put(
                "/api/v1/inventory-counts/{id}/lines/{productId}",
                countId, product).contentType(MediaType.APPLICATION_JSON)
                .content("{\"countedQuantity\":" + quantity
                        + notesProperty + "}"));
    }

    private String createSelectedCount(UUID warehouse, UUID... products)
            throws Exception {
        return performManager(post("/api/v1/inventory-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selectedCountJson(warehouse, products)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
    }

    private List<Integer> createCountsConcurrently(
            UUID warehouse, UUID product) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/inventory-counts")
                                .header(AUTHORIZATION,
                                        "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selectedCountJson(
                                        warehouse, product)))
                        .andReturn().getResponse().getStatus();
            };
            return executor.invokeAll(List.of(task, task)).stream()
                    .map(this::await).sorted().toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private int await(Future<Integer> future) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private UUID createWarehouse(String code) throws Exception {
        String location = performManager(post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","active":true}
                                """.formatted(code, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private UUID createProduct(String sku) throws Exception {
        String location = performManager(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":10,"active":true}
                                """.formatted(sku, sku)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private void adjust(UUID warehouse, UUID product, int quantity)
            throws Exception {
        performManager(patch(
                "/api/v1/warehouses/{warehouse}/inventory/{product}/adjustments",
                warehouse, product).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantityDelta\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private UUID createSalesOrder(UUID warehouse, UUID product, int quantity)
            throws Exception {
        String location = performSales(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fulfillmentWarehouseId":"%s",
                                 "items":[{"productId":"%s","quantity":%d}]}
                                """.formatted(warehouse, product, quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private String selectedCountJson(UUID warehouse, UUID... products) {
        String productIds = Arrays.stream(products)
                .map(product -> "\"" + product + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {"warehouseId":"%s","scope":"SELECTED","productIds":[%s]}
                """.formatted(warehouse, productIds);
    }

    private String fullCountJson(UUID warehouse) {
        return """
                {"warehouseId":"%s","scope":"FULL"}
                """.formatted(warehouse);
    }

    private ResultActions performManager(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(
                AUTHORIZATION, "Bearer " + managerToken));
    }

    private ResultActions performSales(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(
                AUTHORIZATION, "Bearer " + salesToken));
    }

    private UUID countId(String response) {
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private Integer lineExpected(UUID countId, UUID product) {
        return jdbcTemplate.queryForObject("""
                SELECT expected_quantity FROM inventory_count_lines
                WHERE count_id = ? AND product_id = ?
                """, Integer.class, countId, product);
    }

    private Integer lineVariance(UUID countId, UUID product) {
        return jdbcTemplate.queryForObject("""
                SELECT variance FROM inventory_count_lines
                WHERE count_id = ? AND product_id = ?
                """, Integer.class, countId, product);
    }

    private int stock(UUID warehouse, UUID product) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE((SELECT quantity FROM inventory
                    WHERE warehouse_id = ? AND product_id = ?), 0)
                """, Integer.class, warehouse, product);
    }

    private int reserved(UUID warehouse, UUID product) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(quantity), 0) FROM inventory_reservations
                WHERE warehouse_id = ? AND product_id = ?
                """, Integer.class, warehouse, product);
    }

    private String countStatus(UUID countId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM inventory_counts WHERE id = ?",
                String.class, countId);
    }

    private int physicalMovementCount(UUID countId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                WHERE movement_type = 'PHYSICAL_COUNT_ADJUSTMENT'
                  AND business_reference = ?
                """, Integer.class, countId.toString());
    }

    private int physicalMovementDelta(UUID countId, UUID product) {
        return jdbcTemplate.queryForObject("""
                SELECT quantity_delta FROM stock_movements
                WHERE movement_type = 'PHYSICAL_COUNT_ADJUSTMENT'
                  AND business_reference = ? AND product_id = ?
                """, Integer.class, countId.toString(), product);
    }
}
