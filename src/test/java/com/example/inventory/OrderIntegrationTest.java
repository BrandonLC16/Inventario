package com.example.inventory;

import com.example.inventory.users.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "orders-password-123";

    private String adminToken;
    private String salesToken;
    private UUID salesUserId;

    @BeforeEach
    void authenticateUsers() throws Exception {
        createUser("order-admin", PASSWORD, true, false, RoleName.ADMIN);
        salesUserId = createUser("order-sales", PASSWORD, true, false, RoleName.SALES).getId();
        adminToken = login("order-admin", PASSWORD);
        salesToken = login("order-sales", PASSWORD);
    }

    @Test
    void orderTransitionsArePersistedAndRepeatedCommandsAreIdempotent() throws Exception {
        UUID productId = createProduct("ORDER-1", 10);
        UUID orderId = createOrder(new RequestedItem(productId, 4));

        performSales(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].quantity").value(4));
        performSales(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()));
        performSales(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict());

        performSales(post("/api/orders/{id}/confirm", orderId))
                .andExpect(status().isConflict());
        performSales(post("/api/orders/{id}/reserve", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.reservedBy").value(salesUserId.toString()))
                .andExpect(jsonPath("$.reservedAt").isNotEmpty());
        performSales(post("/api/orders/{id}/reserve", orderId))
                .andExpect(status().isOk());
        assertEquals(10, stock(productId));
        assertEquals(4, reserved(productId));
        assertEquals(1, movementCount(orderId, "ORDER_RESERVED"));

        performSales(post("/api/orders/{id}/confirm", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        performSales(post("/api/orders/{id}/confirm", orderId))
                .andExpect(status().isOk());
        assertEquals(6, stock(productId));
        assertEquals(1, movementCount(orderId, "ORDER_CONFIRMED"));

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        performSales(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        performSales(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isOk());
        assertEquals(10, stock(productId));
        assertEquals(1, movementCount(orderId, "ORDER_CANCELLED"));
        performSales(post("/api/orders/{id}/confirm", orderId))
                .andExpect(status().isConflict());

        List<String> actors = jdbcTemplate.queryForList("""
                SELECT responsible_user FROM stock_movements
                WHERE business_reference = ? ORDER BY movement_type
                """, String.class, orderId.toString());
        assertEquals(List.of(
                salesUserId.toString(), salesUserId.toString(),
                salesUserId.toString()), actors);
    }

    @Test
    void reservationRollsBackEveryProductWhenOneHasInsufficientStock() throws Exception {
        UUID firstProduct = createProduct("ROLLBACK-1", 5);
        UUID secondProduct = createProduct("ROLLBACK-2", 1);
        UUID orderId = createOrder(
                new RequestedItem(firstProduct, 3),
                new RequestedItem(secondProduct, 2));

        performSales(post("/api/orders/{id}/reserve", orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Inventory quantity cannot be negative"));

        assertEquals(5, stock(firstProduct));
        assertEquals(1, stock(secondProduct));
        assertEquals(0, movementCount(orderId, "ORDER_RESERVED"));
        assertEquals("PENDING", orderStatus(orderId));
    }

    @Test
    void creationRejectsDuplicateOrMissingProductsWithoutSavingOrder() throws Exception {
        UUID productId = createProduct("INVALID-ORDER-1", 0);

        performSales(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"productId":"%s","quantity":1},
                                  {"productId":"%s","quantity":2}
                                ]}
                                """.formatted(productId, productId)))
                .andExpect(status().isBadRequest());
        performSales(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":1}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void cancellationRollsBackEveryRestorationWhenOneBalanceOverflows() throws Exception {
        UUID firstProduct = createProduct("CANCEL-ROLLBACK-1", 10);
        UUID secondProduct = createProduct("CANCEL-ROLLBACK-2", 10);
        UUID orderId = createOrder(
                new RequestedItem(firstProduct, 2),
                new RequestedItem(secondProduct, 2));
        performSales(post("/api/orders/{id}/reserve", orderId))
                .andExpect(status().isOk());
        performSales(post("/api/orders/{id}/confirm", orderId))
                .andExpect(status().isOk());

        UUID lastLocked = firstProduct.compareTo(secondProduct) > 0 ? firstProduct : secondProduct;
        UUID firstLocked = lastLocked.equals(firstProduct) ? secondProduct : firstProduct;
        jdbcTemplate.update("UPDATE inventory SET quantity = ? WHERE product_id = ?",
                Integer.MAX_VALUE, lastLocked);

        performSales(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict());

        assertEquals(8, stock(firstLocked));
        assertEquals(Integer.MAX_VALUE, stock(lastLocked));
        assertEquals(0, movementCount(orderId, "ORDER_CANCELLED"));
        assertEquals("CONFIRMED", orderStatus(orderId));
    }

    @Test
    void concurrentConfirmationAndCancellationApplyInventoryExactlyOnce() throws Exception {
        UUID productId = createProduct("CONCURRENT-1", 10);
        UUID orderId = createOrder(new RequestedItem(productId, 4));
        performSales(post("/api/orders/{id}/reserve", orderId))
                .andExpect(status().isOk());

        assertEquals(List.of(200, 200), concurrently(
                "/api/orders/" + orderId + "/confirm"));
        assertEquals(6, stock(productId));
        assertEquals(1, movementCount(orderId, "ORDER_CONFIRMED"));
        assertEquals("CONFIRMED", orderStatus(orderId));

        assertEquals(List.of(200, 200), concurrently(
                "/api/orders/" + orderId + "/cancel"));
        assertEquals(10, stock(productId));
        assertEquals(1, movementCount(orderId, "ORDER_CANCELLED"));
        assertEquals("CANCELLED", orderStatus(orderId));
    }

    @Test
    void concurrentOrdersCompetingForStockCannotOversell() throws Exception {
        UUID productId = createProduct("COMPETING-1", 5);
        UUID firstOrder = createOrder(new RequestedItem(productId, 4));
        UUID secondOrder = createOrder(new RequestedItem(productId, 4));

        List<Integer> statuses = concurrentlyDifferent(
                "/api/orders/" + firstOrder + "/reserve",
                "/api/orders/" + secondOrder + "/reserve");

        assertEquals(List.of(200, 400), statuses);
        assertEquals(5, stock(productId));
        assertEquals(4, reserved(productId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                WHERE movement_type = 'ORDER_RESERVED'
                """, Integer.class));
        List<String> orderStatuses = jdbcTemplate.queryForList(
                "SELECT status FROM orders ORDER BY status", String.class);
        assertEquals(List.of("PENDING", "RESERVED"), orderStatuses);
    }

    private List<Integer> concurrently(String path) throws Exception {
        return concurrentlyDifferent(path, path);
    }

    private List<Integer> concurrentlyDifferent(String firstPath, String secondPath)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Integer> firstRequest = concurrentRequest(firstPath, barrier);
        Callable<Integer> secondRequest = concurrentRequest(secondPath, barrier);
        try {
            Future<Integer> first = executor.submit(firstRequest);
            Future<Integer> second = executor.submit(secondRequest);
            return Arrays.asList(
                            first.get(30, TimeUnit.SECONDS),
                            second.get(30, TimeUnit.SECONDS)).stream()
                    .sorted().toList();
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

    private UUID createProduct(String sku, int initialStock) throws Exception {
        String location = mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":10,"active":true}
                                """.formatted(sku, sku)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        UUID productId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        if (initialStock > 0) {
            mockMvc.perform(patch("/api/inventory/{id}/adjustments", productId)
                            .header(AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantityDelta\":" + initialStock + "}"))
                    .andExpect(status().isOk());
        }
        return productId;
    }

    private UUID createOrder(RequestedItem... requestedItems) throws Exception {
        String items = Arrays.stream(requestedItems)
                .map(item -> """
                        {"productId":"%s","quantity":%d}
                        """.formatted(item.productId(), item.quantity()).trim())
                .collect(Collectors.joining(","));
        String location = mockMvc.perform(post("/api/orders")
                        .header(AUTHORIZATION, "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[%s]}
                                """.formatted(items)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private org.springframework.test.web.servlet.ResultActions performSales(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + salesToken));
    }

    private int stock(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory WHERE product_id = ?",
                Integer.class, productId);
    }

    private int reserved(UUID productId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(quantity), 0)
                FROM inventory_reservations WHERE product_id = ?
                """, Integer.class, productId);
    }

    private int movementCount(UUID orderId, String movementType) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                WHERE business_reference = ? AND movement_type = ?
                """, Integer.class, orderId.toString(), movementType);
    }

    private String orderStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private record RequestedItem(UUID productId, int quantity) {
    }
}
