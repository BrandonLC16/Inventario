package com.example.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductInventoryIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "regression-password-123";
    private String adminToken;
    private UUID adminId;

    @BeforeEach
    void authenticateAdmin() throws Exception {
        adminId = createUser("regression-admin", PASSWORD, true, false,
                com.example.inventory.users.RoleName.ADMIN).getId();
        adminToken = login("regression-admin", PASSWORD);
    }

    @Test
    void productCrudWorksAgainstPostgres() throws Exception {
        String location = createProduct("kbd-001", "Keyboard");

        performAuthenticated(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("KBD-001"))
                .andExpect(jsonPath("$.price").value(99.90));

        performAuthenticated(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        performAuthenticated(put(location)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("KBD-001", "Updated keyboard", "149.90")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated keyboard"));

        performAuthenticated(delete(location)).andExpect(status().isNoContent());
        performAuthenticated(get(location)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateSkuAndInvalidProductReturnUsefulErrors() throws Exception {
        createProduct("SKU-1", "First");

        performAuthenticated(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("sku-1", "Duplicate", "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SKU SKU-1 already exists"));

        performAuthenticated(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"", "name":"", "price":-1, "active":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.sku").exists())
                .andExpect(jsonPath("$.validationErrors.price").exists());
    }

    @Test
    void stockCanBeReceivedAndConsumedButNeverBecomesNegative() throws Exception {
        UUID productId = idFromLocation(createProduct("STOCK-1", "Stocked product"));

        performAuthenticated(get("/api/v1/inventory/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));

        adjust(productId, 12).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(12));
        adjust(productId, -5).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7));
        adjust(productId, -8).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Inventory quantity cannot be negative"));
    }

    @Test
    void generalInventoryAndMovementListingsArePagedAndFilterable() throws Exception {
        UUID firstProduct = idFromLocation(createProduct("LIST-1", "First listed product"));
        UUID secondProduct = idFromLocation(createProduct("LIST-2", "Second listed product"));
        adjust(firstProduct, 7).andExpect(status().isOk());
        adjust(secondProduct, 3).andExpect(status().isOk());

        performAuthenticated(get("/api/v1/inventory").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].productId").value(firstProduct.toString()))
                .andExpect(jsonPath("$.content[0].quantity").value(7))
                .andExpect(jsonPath("$.content[1].productId").value(secondProduct.toString()))
                .andExpect(jsonPath("$.content[1].quantity").value(3));

        performAuthenticated(get("/api/v1/inventory/movements")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        performAuthenticated(get("/api/v1/inventory/movements")
                        .param("productId", firstProduct.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(firstProduct.toString()));
    }

    @Test
    void manualAdjustmentRecordsAuthenticatedUser() throws Exception {
        UUID productId = idFromLocation(createProduct("AUDIT-1", "Audited product"));

        adjust(productId, 4).andExpect(status().isOk());

        String responsibleUser = jdbcTemplate.queryForObject("""
                SELECT responsible_user FROM stock_movements
                WHERE product_id = ? AND movement_type = 'INITIAL_STOCK'
                """, String.class, productId);
        org.junit.jupiter.api.Assertions.assertEquals(adminId.toString(), responsibleUser);
    }

    @Test
    void inventoryRequiresAnExistingProduct() throws Exception {
        UUID missingId = UUID.randomUUID();
        performAuthenticated(get("/api/v1/inventory/{id}", missingId))
                .andExpect(status().isNotFound());
        adjust(missingId, 1).andExpect(status().isNotFound());
    }

    @Test
    void productWithStockMovementsCanBeDeletedWithoutLosingHistory() throws Exception {
        String location = createProduct("DELETE-1", "Deleted product");
        UUID productId = idFromLocation(location);
        adjust(productId, 5).andExpect(status().isOk());

        performAuthenticated(delete(location)).andExpect(status().isNoContent());
        performAuthenticated(get(location)).andExpect(status().isNotFound());

        Integer movements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE product_id = ?",
                Integer.class, productId);
        org.junit.jupiter.api.Assertions.assertEquals(1, movements);
    }

    @Test
    void flywayAppliedExpectedMigrations() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success AND version IN ('1', '2', '3')", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(3, migrations);
    }

    private String createProduct(String sku, String name) throws Exception {
        return performAuthenticated(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, name, "99.90")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");
    }

    private org.springframework.test.web.servlet.ResultActions adjust(UUID productId, int delta)
            throws Exception {
        return performAuthenticated(patch("/api/v1/inventory/{id}/adjustments", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantityDelta\":%d}".formatted(delta)));
    }

    private org.springframework.test.web.servlet.ResultActions performAuthenticated(
            MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + adminToken));
    }

    private UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private String productJson(String sku, String name, String price) {
        return """
                {"sku":"%s", "name":"%s", "description":"Test product", "price":%s, "active":true}
                """.formatted(sku, name, price);
    }
}
