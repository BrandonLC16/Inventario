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

class WarehouseInventoryIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD = "warehouse-password-123";

    private String adminToken;
    private String salesToken;

    @BeforeEach
    void authenticateActors() throws Exception {
        createUser("warehouse-admin", PASSWORD, true, false, RoleName.ADMIN);
        createUser("warehouse-sales", PASSWORD, true, false, RoleName.SALES);
        adminToken = login("warehouse-admin", PASSWORD);
        salesToken = login("warehouse-sales", PASSWORD);
    }

    @Test
    void sameProductBalancesMovementsAndAlertsAreIsolatedByWarehouse() throws Exception {
        UUID first = createWarehouse("ISO-A");
        UUID second = createWarehouse("ISO-B");
        UUID product = createProduct("WAREHOUSE-ISO", 0);
        configure(first, product, 5);
        configure(second, product, 5);

        adjust(first, product, 3).andExpect(status().isOk());
        adjust(second, product, 8).andExpect(status().isOk());

        balance(first, product).andExpect(jsonPath("$.quantity").value(3));
        balance(second, product).andExpect(jsonPath("$.quantity").value(8));
        authenticated(get("/api/v1/warehouses/{id}/inventory/movements", first)
                        .param("productId", product.toString()), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].warehouseId").value(first.toString()))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(3));
        authenticated(get("/api/v1/warehouses/{id}/inventory/low-stock", first), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].warehouseId").value(first.toString()))
                .andExpect(jsonPath("$.content[0].minimumStock").value(5));
        authenticated(get("/api/v1/warehouses/{id}/inventory/low-stock", second), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void catalogUpdatePreservesMinimumAndActivationInEveryWarehouse() throws Exception {
        UUID warehouse = createWarehouse("CATALOG-SEPARATION");
        UUID product = createProduct("CATALOG-PRODUCT", 3);
        UUID main = UUID.fromString("00000000-0000-0000-0000-000000000001");
        configure(main, product, 11, false);
        configure(warehouse, product, 7, false);

        authenticated(put("/api/v1/products/{id}", product)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"CATALOG-PRODUCT","name":"Updated catalog name",
                                 "description":"Updated catalog description",
                                 "price":25.50,"active":true}
                                """), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated catalog name"))
                .andExpect(jsonPath("$.description")
                        .value("Updated catalog description"))
                .andExpect(jsonPath("$.price").value(25.50));

        assertSetting(main, product, 11, false);
        assertSetting(warehouse, product, 7, false);
    }

    @Test
    void reservationsCompeteInsideOneWarehouseButRemainIndependentAcrossWarehouses()
            throws Exception {
        UUID first = createWarehouse("RES-A");
        UUID second = createWarehouse("RES-B");
        UUID product = createProduct("WAREHOUSE-RES", 0);
        adjust(first, product, 5);
        adjust(second, product, 5);

        UUID firstOrder = createOrder(first, product, 4);
        UUID competingOrder = createOrder(first, product, 4);
        UUID independentOrder = createOrder(second, product, 4);

        assertEquals(List.of(200, 400), reserveConcurrently(firstOrder, competingOrder));
        authenticated(post("/api/v1/orders/{id}/reserve", independentOrder), salesToken)
                .andExpect(status().isOk());
        balance(first, product)
                .andExpect(jsonPath("$.reservedQuantity").value(4))
                .andExpect(jsonPath("$.availableQuantity").value(1));
        balance(second, product)
                .andExpect(jsonPath("$.reservedQuantity").value(4))
                .andExpect(jsonPath("$.availableQuantity").value(1));
    }

    @Test
    void reverseProductOrderUsesDeterministicLocksWithoutDeadlock() throws Exception {
        UUID warehouse = createWarehouse("LOCK-ORDER");
        UUID firstProduct = createProduct("LOCK-PRODUCT-A", 0);
        UUID secondProduct = createProduct("LOCK-PRODUCT-B", 0);
        adjust(warehouse, firstProduct, 2);
        adjust(warehouse, secondProduct, 2);
        UUID firstOrder = createTwoProductOrder(
                warehouse, firstProduct, secondProduct);
        UUID reverseOrder = createTwoProductOrder(
                warehouse, secondProduct, firstProduct);

        assertEquals(List.of(200, 200), reserveConcurrently(firstOrder, reverseOrder));
        balance(warehouse, firstProduct)
                .andExpect(jsonPath("$.reservedQuantity").value(2))
                .andExpect(jsonPath("$.availableQuantity").value(0));
        balance(warehouse, secondProduct)
                .andExpect(jsonPath("$.reservedQuantity").value(2))
                .andExpect(jsonPath("$.availableQuantity").value(0));
    }
    @Test
    void cancellationRestoresStockOnlyInTheOriginalWarehouse() throws Exception {
        UUID first = createWarehouse("CANCEL-A");
        UUID second = createWarehouse("CANCEL-B");
        UUID product = createProduct("WAREHOUSE-CANCEL", 0);
        adjust(first, product, 5);
        adjust(second, product, 8);
        UUID order = createOrder(first, product, 2);

        authenticated(post("/api/v1/orders/{id}/reserve", order), salesToken)
                .andExpect(status().isOk());
        authenticated(post("/api/v1/orders/{id}/confirm", order), salesToken)
                .andExpect(status().isOk());
        authenticated(post("/api/v1/orders/{id}/cancel", order), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentWarehouseId").value(first.toString()));

        balance(first, product).andExpect(jsonPath("$.quantity").value(5));
        balance(second, product).andExpect(jsonPath("$.quantity").value(8));
    }

    @Test
    void reservedOrderMustBeReleasedBeforeItsWarehouseCanChange() throws Exception {
        UUID first = createWarehouse("MOVE-A");
        UUID second = createWarehouse("MOVE-B");
        UUID product = createProduct("WAREHOUSE-MOVE", 0);
        adjust(first, product, 5);
        UUID order = createOrder(first, product, 2);
        authenticated(post("/api/v1/orders/{id}/reserve", order), salesToken)
                .andExpect(status().isOk());

        replaceOrder(order, second, product)
                .andExpect(status().isConflict());
        authenticated(post("/api/v1/orders/{id}/release", order), salesToken)
                .andExpect(status().isOk());
        replaceOrder(order, second, product)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentWarehouseId").value(second.toString()));
    }

    @Test
    void warehouseWithStockCannotBeDeactivatedAndInactiveWarehouseRejectsAdjustments()
            throws Exception {
        UUID stocked = createWarehouse("ACTIVE-STOCK");
        UUID empty = createWarehouse("EMPTY-INACTIVE");
        UUID open = createWarehouse("OPEN-ORDER");
        UUID product = createProduct("WAREHOUSE-ACTIVE", 0);
        adjust(stocked, product, 1);

        authenticated(delete("/api/v1/warehouses/{id}", stocked), adminToken)
                .andExpect(status().isConflict());
        createOrder(open, product, 1);
        authenticated(delete("/api/v1/warehouses/{id}", open), adminToken)
                .andExpect(status().isConflict());
        authenticated(delete("/api/v1/warehouses/{id}", empty), adminToken)
                .andExpect(status().isNoContent());
        adjust(empty, product, 1).andExpect(status().isConflict());
    }

    private UUID createWarehouse(String code) throws Exception {
        String location = authenticated(post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","active":true}
                                """.formatted(code, code)), adminToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private UUID createProduct(String sku, int minimumStock) throws Exception {
        String location = authenticated(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":10,
                                 "active":true,"minimumStock":%d}
                                """.formatted(sku, sku, minimumStock)), adminToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void configure(UUID warehouse, UUID product, int minimumStock) throws Exception {
        configure(warehouse, product, minimumStock, true);
    }

    private void configure(UUID warehouse, UUID product, int minimumStock, boolean active)
            throws Exception {
        authenticated(put("/api/v1/warehouses/{warehouse}/inventory/{product}/settings",
                        warehouse, product).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minimumStock":%d,"active":%s}
                                """.formatted(minimumStock, active)), adminToken)
                .andExpect(status().isNoContent());
    }

    private void assertSetting(UUID warehouse, UUID product, int minimumStock,
                               boolean active) {
        var setting = jdbcTemplate.queryForMap("""
                SELECT minimum_stock, active
                FROM warehouse_product_settings
                WHERE warehouse_id = ? AND product_id = ?
                """, warehouse, product);
        assertEquals(minimumStock, ((Number) setting.get("minimum_stock")).intValue());
        assertEquals(active, setting.get("active"));
    }

    private ResultActions adjust(UUID warehouse, UUID product, int quantity) throws Exception {
        return authenticated(patch(
                "/api/v1/warehouses/{warehouse}/inventory/{product}/adjustments",
                warehouse, product).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantityDelta":%d}
                        """.formatted(quantity)), adminToken);
    }

    private ResultActions balance(UUID warehouse, UUID product) throws Exception {
        return authenticated(get("/api/v1/warehouses/{warehouse}/inventory/{product}",
                warehouse, product), salesToken).andExpect(status().isOk());
    }

    private UUID createOrder(UUID warehouse, UUID product, int quantity) throws Exception {
        String location = authenticated(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fulfillmentWarehouseId":"%s",
                                 "items":[{"productId":"%s","quantity":%d}]}
                                """.formatted(warehouse, product, quantity)), salesToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private UUID createTwoProductOrder(UUID warehouse, UUID firstProduct,
                                       UUID secondProduct) throws Exception {
        String location = authenticated(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fulfillmentWarehouseId":"%s","items":[
                                  {"productId":"%s","quantity":1},
                                  {"productId":"%s","quantity":1}]}
                                """.formatted(warehouse, firstProduct, secondProduct)), salesToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
    private ResultActions replaceOrder(UUID order, UUID warehouse, UUID product)
            throws Exception {
        return authenticated(put("/api/v1/orders/{id}/items", order)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fulfillmentWarehouseId":"%s",
                         "items":[{"productId":"%s","quantity":2}]}
                        """.formatted(warehouse, product)), salesToken);
    }

    private List<Integer> reserveConcurrently(UUID... orders) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(orders.length);
        ExecutorService executor = Executors.newFixedThreadPool(orders.length);
        try {
            List<Callable<Integer>> tasks = Arrays.stream(orders)
                    .map(order -> (Callable<Integer>) () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return mockMvc.perform(post("/api/v1/orders/{id}/reserve", order)
                                        .header(AUTHORIZATION, "Bearer " + salesToken))
                                .andReturn().getResponse().getStatus();
                    }).toList();
            List<Integer> statuses = executor.invokeAll(tasks).stream()
                    .map(this::await).sorted().toList();
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private int await(Future<Integer> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private ResultActions authenticated(MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + token));
    }
}
