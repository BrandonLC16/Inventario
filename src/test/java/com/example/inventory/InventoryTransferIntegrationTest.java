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
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryTransferIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "transfers-password-123";

    private String managerToken;
    private String salesToken;

    @BeforeEach
    void authenticateActors() throws Exception {
        createUser("transfer-manager", PASSWORD, true, false,
                RoleName.INVENTORY_MANAGER);
        createUser("transfer-sales", PASSWORD, true, false, RoleName.SALES);
        managerToken = login("transfer-manager", PASSWORD);
        salesToken = login("transfer-sales", PASSWORD);
    }

    @Test
    void dispatchAndReceiptAreIdempotentAndConserveAllUnits() throws Exception {
        UUID source = createWarehouse("TRANSFER-LIFE-SOURCE");
        UUID destination = createWarehouse("TRANSFER-LIFE-DEST");
        UUID product = createProduct("TRANSFER-LIFE-PRODUCT");
        adjust(source, product, 10);

        String created = createTransfer(source, destination,
                new RequestedItem(product, 4));
        UUID transferId = UUID.fromString(JsonPath.read(created, "$.id"));
        UUID itemId = UUID.fromString(JsonPath.read(created, "$.items[0].id"));
        String folio = JsonPath.read(created, "$.folio");
        assertEquals(10, conservedUnits(transferId, source, destination, product));
        performManager(delete("/api/v1/warehouses/{id}", destination))
                .andExpect(status().isConflict());

        performManager(put("/api/v1/inventory-transfers/{id}/items", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(new RequestedItem(product, 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.totalQuantity").value(5));

        performManager(post("/api/v1/inventory-transfers/{id}/dispatch", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.inTransitQuantity").value(5))
                .andExpect(jsonPath("$.items[0].inTransitQuantity").value(5))
                .andExpect(jsonPath("$.dispatchedAt").isNotEmpty());
        performManager(post("/api/v1/inventory-transfers/{id}/dispatch", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        assertEquals(5, stock(source, product));
        assertEquals(0, stock(destination, product));
        assertEquals(1, movementCount(transferId, "TRANSFER_OUT"));
        assertEquals(10, conservedUnits(transferId, source, destination, product));
        performManager(put("/api/v1/inventory-transfers/{id}/items", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(new RequestedItem(product, 1))))
                .andExpect(status().isConflict());
        performManager(post("/api/v1/inventory-transfers/{id}/cancel", transferId))
                .andExpect(status().isConflict());

        performManager(get("/api/v1/inventory-transfers")
                        .param("status", "IN_TRANSIT")
                        .param("sourceWarehouseId", source.toString())
                        .param("destinationWarehouseId", destination.toString())
                        .param("folio", folio.substring(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transferId.toString()));

        performManager(post("/api/v1/inventory-transfers/{id}/receive", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.inTransitQuantity").value(0))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());
        performManager(post("/api/v1/inventory-transfers/{id}/receive", transferId))
                .andExpect(status().isOk());
        performManager(post("/api/v1/inventory-transfers/{id}/dispatch", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        assertEquals(5, stock(source, product));
        assertEquals(5, stock(destination, product));
        assertEquals(1, movementCount(transferId, "TRANSFER_IN"));
        assertEquals(10, conservedUnits(transferId, source, destination, product));
    }

    @Test
    void competingTransfersCannotDispatchTheSameStock() throws Exception {
        UUID source = createWarehouse("TRANSFER-COMPETE-SOURCE");
        UUID destination = createWarehouse("TRANSFER-COMPETE-DEST");
        UUID product = createProduct("TRANSFER-COMPETE-PRODUCT");
        adjust(source, product, 5);
        UUID first = transferId(createTransfer(source, destination,
                new RequestedItem(product, 4)));
        UUID second = transferId(createTransfer(source, destination,
                new RequestedItem(product, 4)));

        assertEquals(List.of(200, 400), dispatchConcurrently(first, second));
        assertEquals(1, stock(source, product));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM inventory_transfers
                WHERE status = 'IN_TRANSIT' AND id IN (?, ?)
                """, Integer.class, first, second));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                WHERE movement_type = 'TRANSFER_OUT'
                  AND business_reference IN (?, ?)
                """, Integer.class, first.toString(), second.toString()));
    }

    @Test
    void dispatchNeverConsumesUnitsReservedForSales() throws Exception {
        UUID source = createWarehouse("TRANSFER-RESERVED-SOURCE");
        UUID destination = createWarehouse("TRANSFER-RESERVED-DEST");
        UUID product = createProduct("TRANSFER-RESERVED-PRODUCT");
        adjust(source, product, 10);
        UUID orderId = createSalesOrder(source, product, 7);
        performSales(post("/api/v1/orders/{id}/reserve", orderId))
                .andExpect(status().isOk());
        UUID transferId = transferId(createTransfer(source, destination,
                new RequestedItem(product, 4)));

        performManager(post("/api/v1/inventory-transfers/{id}/dispatch", transferId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Insufficient available inventory for transfer"));
        assertEquals(10, stock(source, product));
        assertEquals(7, reserved(source, product));
        assertEquals("DRAFT", transferStatus(transferId));

        performManager(put("/api/v1/inventory-transfers/{id}/items", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(new RequestedItem(product, 3))))
                .andExpect(status().isOk());
        performManager(post("/api/v1/inventory-transfers/{id}/dispatch", transferId))
                .andExpect(status().isOk());
        assertEquals(7, stock(source, product));
        assertEquals(7, reserved(source, product));
        assertEquals(0, available(source, product));
    }

    @Test
    void multiItemDispatchRollsBackEarlierProductsWhenOneIsInsufficient()
            throws Exception {
        UUID source = createWarehouse("TRANSFER-ROLLBACK-SOURCE");
        UUID destination = createWarehouse("TRANSFER-ROLLBACK-DEST");
        UUID firstProduct = createProduct("TRANSFER-ROLLBACK-A");
        UUID secondProduct = createProduct("TRANSFER-ROLLBACK-B");
        UUID firstLocked = firstProduct.compareTo(secondProduct) < 0
                ? firstProduct : secondProduct;
        UUID lastLocked = firstLocked.equals(firstProduct)
                ? secondProduct : firstProduct;
        adjust(source, firstLocked, 5);
        adjust(source, lastLocked, 1);
        UUID transferId = transferId(createTransfer(source, destination,
                new RequestedItem(firstLocked, 2),
                new RequestedItem(lastLocked, 2)));

        performManager(post("/api/v1/inventory-transfers/{id}/dispatch", transferId))
                .andExpect(status().isBadRequest());

        assertEquals(5, stock(source, firstLocked));
        assertEquals(1, stock(source, lastLocked));
        assertEquals(0, movementCount(transferId, "TRANSFER_OUT"));
        assertEquals("DRAFT", transferStatus(transferId));
    }

    @Test
    void warehousesMustBeDifferentAndActiveAndOnlyDraftCanBeCancelled()
            throws Exception {
        UUID source = createWarehouse("TRANSFER-RULE-SOURCE");
        UUID destination = createWarehouse("TRANSFER-RULE-DEST");
        UUID inactive = createWarehouse("TRANSFER-RULE-INACTIVE");
        UUID product = createProduct("TRANSFER-RULE-PRODUCT");

        performManager(post("/api/v1/inventory-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, source,
                                new RequestedItem(product, 1))))
                .andExpect(status().isBadRequest());
        performManager(delete("/api/v1/warehouses/{id}", inactive))
                .andExpect(status().isNoContent());
        performManager(post("/api/v1/inventory-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(inactive, destination,
                                new RequestedItem(product, 1))))
                .andExpect(status().isConflict());

        UUID transferId = transferId(createTransfer(source, destination,
                new RequestedItem(product, 1)));
        performManager(post("/api/v1/inventory-transfers/{id}/cancel", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
        performManager(post("/api/v1/inventory-transfers/{id}/dispatch", transferId))
                .andExpect(status().isConflict());
        performManager(put("/api/v1/inventory-transfers/{id}/items", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(new RequestedItem(product, 2))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/inventory-transfers")
                        .header(AUTHORIZATION, "Bearer " + salesToken))
                .andExpect(status().isForbidden());
    }

    private List<Integer> dispatchConcurrently(UUID... transferIds)
            throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(transferIds.length);
        ExecutorService executor = Executors.newFixedThreadPool(transferIds.length);
        try {
            List<Callable<Integer>> tasks = Arrays.stream(transferIds)
                    .map(id -> (Callable<Integer>) () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return mockMvc.perform(post(
                                        "/api/v1/inventory-transfers/{id}/dispatch", id)
                                        .header(AUTHORIZATION,
                                                "Bearer " + managerToken))
                                .andReturn().getResponse().getStatus();
                    }).toList();
            return executor.invokeAll(tasks).stream()
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

    private String createTransfer(UUID source, UUID destination,
                                  RequestedItem... items) throws Exception {
        return performManager(post("/api/v1/inventory-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, items)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
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

    private String transferJson(UUID source, UUID destination,
                                RequestedItem... items) {
        return """
                {"sourceWarehouseId":"%s","destinationWarehouseId":"%s",
                 "items":[%s]}
                """.formatted(source, destination, itemJson(items));
    }

    private String updateJson(RequestedItem... items) {
        return "{\"items\":[" + itemJson(items) + "]}";
    }

    private String itemJson(RequestedItem... items) {
        return Arrays.stream(items)
                .map(item -> """
                        {"productId":"%s","quantity":%d}
                        """.formatted(item.productId(), item.quantity()))
                .collect(java.util.stream.Collectors.joining(","));
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

    private UUID transferId(String response) {
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
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

    private int available(UUID warehouse, UUID product) {
        return stock(warehouse, product) - reserved(warehouse, product);
    }

    private int movementCount(UUID transferId, String type) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                WHERE movement_type = ? AND business_reference = ?
                """, Integer.class, type, transferId.toString());
    }

    private int conservedUnits(UUID transferId, UUID source,
                               UUID destination, UUID product) {
        int inTransit = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN transfer.status = 'IN_TRANSIT'
                    THEN item.quantity ELSE 0 END
                FROM inventory_transfers transfer
                JOIN inventory_transfer_items item
                  ON item.transfer_id = transfer.id
                WHERE transfer.id = ? AND item.product_id = ?
                """, Integer.class, transferId, product);
        return stock(source, product) + inTransit + stock(destination, product);
    }

    private String transferStatus(UUID transferId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM inventory_transfers WHERE id = ?",
                String.class, transferId);
    }

    private record RequestedItem(UUID productId, int quantity) {
    }
}
