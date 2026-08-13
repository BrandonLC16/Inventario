package com.example.inventory;

import com.example.inventory.users.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryAdjustmentConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "concurrent-adjustments-password-123";

    private String adminToken;

    @BeforeEach
    void authenticateAdmin() throws Exception {
        createUser("concurrent-adjustments-admin", PASSWORD, true, false, RoleName.ADMIN);
        adminToken = login("concurrent-adjustments-admin", PASSWORD);
    }

    @Test
    void simultaneousReceiptsInitializeStockOnceWithoutLostUpdates() throws Exception {
        UUID productId = createProduct("CONCURRENT-IN-1");
        assertEquals(0, inventoryRowCount(productId));

        List<ConcurrentResponse> responses = adjustConcurrently(productId, 7, 11);

        assertEquals(List.of(200, 200), sortedStatuses(responses));
        assertEquals(18, stock(productId));
        assertEquals(1, inventoryRowCount(productId));

        List<Movement> movements = movements(productId);
        assertEquals(2, movements.size());
        assertEquals(List.of("INITIAL_STOCK", "MANUAL_IN"),
                movements.stream().map(Movement::type).toList());
        assertEquals(List.of(7, 11),
                movements.stream().map(Movement::delta).sorted().toList());
        assertEquals(0, movements.getFirst().balanceBefore());
        assertEquals(movements.getFirst().balanceAfter(),
                movements.getLast().balanceBefore());
        assertEquals(18, movements.getLast().balanceAfter());
        movements.forEach(movement ->
                assertEquals(movement.balanceBefore() + movement.delta(),
                        movement.balanceAfter()));
    }

    @Test
    void simultaneousWithdrawalsAllowOnlyOneWhenStockIsLimited() throws Exception {
        UUID productId = createProduct("CONCURRENT-OUT-1");
        adjust(productId, 5);
        int movementsBefore = movementCount(productId);

        List<ConcurrentResponse> responses = adjustConcurrently(productId, -4, -4);

        assertEquals(List.of(200, 400), sortedStatuses(responses));
        assertEquals("Inventory quantity cannot be negative",
                responses.stream()
                        .filter(response -> response.status() == 400)
                        .map(ConcurrentResponse::body)
                        .map(body -> com.jayway.jsonpath.JsonPath.<String>read(
                                body, "$.message"))
                        .findFirst().orElseThrow());
        assertEquals(1, stock(productId));
        assertTrue(stock(productId) >= 0);
        assertEquals(movementsBefore + 1, movementCount(productId));

        List<Movement> movements = movements(productId);
        assertEquals(List.of("INITIAL_STOCK", "MANUAL_OUT"),
                movements.stream().map(Movement::type).toList());
        assertEquals(List.of(5, -4), movements.stream().map(Movement::delta).toList());
        assertEquals(5, movements.getLast().balanceBefore());
        assertEquals(1, movements.getLast().balanceAfter());
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                WHERE product_id = ? AND (balance_before < 0 OR balance_after < 0)
                """, Integer.class, productId));
    }

    private List<ConcurrentResponse> adjustConcurrently(UUID productId, int firstDelta,
                                                         int secondDelta) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<ConcurrentResponse> first = executor.submit(
                    concurrentAdjustment(productId, firstDelta, barrier));
            Future<ConcurrentResponse> second = executor.submit(
                    concurrentAdjustment(productId, secondDelta, barrier));
            return List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<ConcurrentResponse> concurrentAdjustment(UUID productId, int delta,
                                                               CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            var response = mockMvc.perform(patch("/api/v1/inventory/{id}/adjustments", productId)
                            .header(AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantityDelta":%d}
                                    """.formatted(delta)))
                    .andReturn().getResponse();
            return new ConcurrentResponse(response.getStatus(), response.getContentAsString());
        };
    }

    private void adjust(UUID productId, int delta) throws Exception {
        mockMvc.perform(patch("/api/v1/inventory/{id}/adjustments", productId)
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":%d}
                                """.formatted(delta)))
                .andExpect(status().isOk());
    }

    private UUID createProduct(String sku) throws Exception {
        String location = mockMvc.perform(post("/api/v1/products")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":10,"active":true}
                                """.formatted(sku, sku)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private int stock(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory WHERE product_id = ?",
                Integer.class, productId);
    }

    private int inventoryRowCount(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE product_id = ?",
                Integer.class, productId);
    }

    private int movementCount(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE product_id = ?",
                Integer.class, productId);
    }

    private List<Movement> movements(UUID productId) {
        return jdbcTemplate.query("""
                        SELECT movement_type, quantity_delta, balance_before, balance_after
                        FROM stock_movements
                        WHERE product_id = ?
                        ORDER BY balance_before, balance_after
                        """,
                (result, rowNumber) -> new Movement(
                        result.getString("movement_type"),
                        result.getInt("quantity_delta"),
                        result.getInt("balance_before"),
                        result.getInt("balance_after")),
                productId);
    }

    private List<Integer> sortedStatuses(List<ConcurrentResponse> responses) {
        return responses.stream().map(ConcurrentResponse::status).sorted().toList();
    }

    private record ConcurrentResponse(int status, String body) {
    }

    private record Movement(String type, int delta, int balanceBefore, int balanceAfter) {
    }
}
