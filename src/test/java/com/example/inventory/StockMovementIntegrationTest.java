package com.example.inventory;

import com.example.inventory.users.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockMovementIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "movements-password-123";

    private String adminToken;
    private String managerToken;
    private String salesToken;
    private UUID adminId;
    private UUID managerId;
    private UUID salesId;

    @BeforeEach
    void authenticateUsers() throws Exception {
        adminId = createUser("movement-admin", PASSWORD, true, false,
                RoleName.ADMIN).getId();
        managerId = createUser("movement-manager", PASSWORD, true, false,
                RoleName.INVENTORY_MANAGER).getId();
        salesId = createUser("movement-sales", PASSWORD, true, false,
                RoleName.SALES).getId();
        adminToken = login("movement-admin", PASSWORD);
        managerToken = login("movement-manager", PASSWORD);
        salesToken = login("movement-sales", PASSWORD);
    }

    @Test
    void returnsManualAndOrderMovementsNewestFirstWithCompleteTraceability()
            throws Exception {
        UUID productId = createProduct("KARDEX-1");
        adjust(productId, 10, adminToken);
        adjust(productId, -2, managerToken);
        UUID orderId = createAndConfirmOrder(productId, 3);
        cancelOrder(orderId);
        setTime(productId, "INITIAL_STOCK", "2026-01-01T00:00:00Z");
        setTime(productId, "MANUAL_OUT", "2026-01-02T00:00:00Z");
        setTime(productId, "ORDER_RESERVED", "2026-01-02T12:00:00Z");
        setTime(productId, "ORDER_CONFIRMED", "2026-01-03T00:00:00Z");
        setTime(productId, "ORDER_CANCELLED", "2026-01-04T00:00:00Z");

        mockMvc.perform(movements(productId, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].movementType")
                        .value("ORDER_CANCELLED"))
                .andExpect(jsonPath("$.content[0].quantityDelta").value(3))
                .andExpect(jsonPath("$.content[0].balanceBefore").value(5))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(8))
                .andExpect(jsonPath("$.content[0].businessReference")
                        .value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].occurredAt")
                        .value("2026-01-04T00:00:00Z"))
                .andExpect(jsonPath("$.content[0].responsibleUser")
                        .value(salesId.toString()))
                .andExpect(jsonPath("$.content[1].movementType")
                        .value("ORDER_CONFIRMED"))
                .andExpect(jsonPath("$.content[1].quantityDelta").value(-3))
                .andExpect(jsonPath("$.content[1].reservationDelta").value(-3))
                .andExpect(jsonPath("$.content[1].reservedBefore").value(3))
                .andExpect(jsonPath("$.content[1].reservedAfter").value(0))
                .andExpect(jsonPath("$.content[2].movementType")
                        .value("ORDER_RESERVED"))
                .andExpect(jsonPath("$.content[2].quantityDelta").value(0))
                .andExpect(jsonPath("$.content[2].balanceBefore").value(8))
                .andExpect(jsonPath("$.content[2].balanceAfter").value(8))
                .andExpect(jsonPath("$.content[2].reservationDelta").value(3))
                .andExpect(jsonPath("$.content[2].reservedBefore").value(0))
                .andExpect(jsonPath("$.content[2].reservedAfter").value(3))
                .andExpect(jsonPath("$.content[3].movementType").value("MANUAL_OUT"))
                .andExpect(jsonPath("$.content[3].businessReference")
                        .value(startsWith("MANUAL:")))
                .andExpect(jsonPath("$.content[3].responsibleUser")
                        .value(managerId.toString()))
                .andExpect(jsonPath("$.content[4].responsibleUser")
                        .value(adminId.toString()));
    }

    @Test
    void paginatesMovementsWithStableDescendingOrder() throws Exception {
        UUID productId = createProduct("KARDEX-PAGE");
        for (int index = 0; index < 5; index++) {
            adjust(productId, 1, managerToken);
        }
        jdbcTemplate.update("""
                UPDATE stock_movements
                SET occurred_at = TIMESTAMPTZ '2026-02-01T00:00:00Z'
                    + balance_after * INTERVAL '1 second'
                WHERE product_id = ?
                """, productId);

        mockMvc.perform(movements(productId, managerToken)
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(5))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(4));

        mockMvc.perform(movements(productId, managerToken)
                        .param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(1));
    }

    @Test
    void filtersByTypeDatesAndExactBusinessReference() throws Exception {
        UUID productId = createProduct("KARDEX-FILTER");
        adjust(productId, 10, adminToken);
        adjust(productId, -1, managerToken);
        UUID orderId = createAndConfirmOrder(productId, 2);
        cancelOrder(orderId);
        setTime(productId, "INITIAL_STOCK", "2026-03-01T00:00:00Z");
        setTime(productId, "MANUAL_OUT", "2026-03-02T00:00:00Z");
        setTime(productId, "ORDER_RESERVED", "2026-03-02T12:00:00Z");
        setTime(productId, "ORDER_CONFIRMED", "2026-03-03T00:00:00Z");
        setTime(productId, "ORDER_CANCELLED", "2026-03-04T00:00:00Z");

        mockMvc.perform(movements(productId, managerToken)
                        .param("type", "MANUAL_OUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movementType").value("MANUAL_OUT"));
        mockMvc.perform(movements(productId, managerToken)
                        .param("reference", orderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].businessReference")
                        .value(orderId.toString()))
                .andExpect(jsonPath("$.content[1].businessReference")
                        .value(orderId.toString()))
                .andExpect(jsonPath("$.content[2].businessReference")
                        .value(orderId.toString()));
        mockMvc.perform(movements(productId, managerToken)
                        .param("from", "2026-03-02T00:00:00Z")
                        .param("to", "2026-03-03T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].movementType")
                        .value("ORDER_CONFIRMED"))
                .andExpect(jsonPath("$.content[1].movementType")
                        .value("ORDER_RESERVED"))
                .andExpect(jsonPath("$.content[2].movementType").value("MANUAL_OUT"));

        mockMvc.perform(movements(productId, managerToken)
                        .param("type", "ORDER_CANCELLED")
                        .param("reference", orderId.toString())
                        .param("from", "2026-03-04T00:00:00Z")
                        .param("to", "2026-03-04T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(movements(productId, managerToken)
                        .param("from", "2026-03-05T00:00:00Z")
                        .param("to", "2026-03-04T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("The from date must not be after the to date"));
        mockMvc.perform(movements(productId, managerToken).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Page size must be between 1 and 100"));
    }

    @Test
    void returnsHistoricalMovementsAfterProductIsSoftDeleted() throws Exception {
        UUID productId = createProduct("KARDEX-DELETED");
        adjust(productId, 7, managerToken);
        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(movements(productId, managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productId")
                        .value(productId.toString()));
        mockMvc.perform(movements(UUID.randomUUID(), managerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void kardexRequiresAdminOrInventoryManager() throws Exception {
        UUID productId = createProduct("KARDEX-SECURITY");
        adjust(productId, 1, adminToken);

        mockMvc.perform(movements(productId, adminToken)).andExpect(status().isOk());
        mockMvc.perform(movements(productId, managerToken)).andExpect(status().isOk());
        mockMvc.perform(movements(productId, salesToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/inventory/{id}/movements", productId))
                .andExpect(status().isUnauthorized());
    }

    private UUID createProduct(String sku) throws Exception {
        String location = mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":10,"active":true}
                                """.formatted(sku, sku)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void adjust(UUID productId, int delta, String token) throws Exception {
        mockMvc.perform(patch("/api/inventory/{id}/adjustments", productId)
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":" + delta + "}"))
                .andExpect(status().isOk());
    }

    private UUID createAndConfirmOrder(UUID productId, int quantity) throws Exception {
        String location = mockMvc.perform(post("/api/orders")
                        .header(AUTHORIZATION, "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":%d}]}
                                """.formatted(productId, quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        UUID orderId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        mockMvc.perform(post("/api/orders/{id}/reserve", orderId)
                        .header(AUTHORIZATION, "Bearer " + salesToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{id}/confirm", orderId)
                        .header(AUTHORIZATION, "Bearer " + salesToken))
                .andExpect(status().isOk());
        return orderId;
    }

    private void cancelOrder(UUID orderId) throws Exception {
        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .header(AUTHORIZATION, "Bearer " + salesToken))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder movements(
            UUID productId, String token) {
        return get("/api/inventory/{id}/movements", productId)
                .header(AUTHORIZATION, "Bearer " + token);
    }

    private void setTime(UUID productId, String type, String occurredAt) {
        int updated = jdbcTemplate.update("""
                UPDATE stock_movements SET occurred_at = ?
                WHERE product_id = ? AND movement_type = ?
                """, Timestamp.from(Instant.parse(occurredAt)), productId, type);
        assertEquals(1, updated);
    }
}
