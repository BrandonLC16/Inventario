package com.example.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductInventoryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void productCrudWorksAgainstPostgres() throws Exception {
        String location = createProduct("kbd-001", "Keyboard");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("KBD-001"))
                .andExpect(jsonPath("$.price").value(99.90));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put(location)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("KBD-001", "Updated keyboard", "149.90")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated keyboard"));

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateSkuAndInvalidProductReturnUsefulErrors() throws Exception {
        createProduct("SKU-1", "First");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("sku-1", "Duplicate", "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SKU SKU-1 already exists"));

        mockMvc.perform(post("/api/products")
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

        mockMvc.perform(get("/api/inventory/{id}", productId))
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
    void inventoryRequiresAnExistingProduct() throws Exception {
        UUID missingId = UUID.randomUUID();
        mockMvc.perform(get("/api/inventory/{id}", missingId))
                .andExpect(status().isNotFound());
        adjust(missingId, 1).andExpect(status().isNotFound());
    }

    @Test
    void flywayAppliedInitialMigration() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, migrations);
    }

    private String createProduct(String sku, String name) throws Exception {
        return mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, name, "99.90")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");
    }

    private org.springframework.test.web.servlet.ResultActions adjust(UUID productId, int delta)
            throws Exception {
        return mockMvc.perform(patch("/api/inventory/{id}/adjustments", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantityDelta\":%d}".formatted(delta)));
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
