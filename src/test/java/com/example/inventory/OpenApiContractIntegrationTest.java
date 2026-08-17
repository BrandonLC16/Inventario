package com.example.inventory;

import com.example.inventory.users.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "inventory.security.swagger-enabled=true",
        "springdoc.api-docs.enabled=true"
})
class OpenApiContractIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "openapi-password-123";

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void generatesAReproducibleVersionedContract() throws Exception {
        createUser("openapi-admin", PASSWORD, true, false, RoleName.ADMIN);
        String adminToken = login("openapi-admin", PASSWORD);

        String response = mockMvc.perform(get("/v3/api-docs")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode contract = objectMapper.readTree(response);
        Collection<String> paths = contract.path("paths").propertyNames();
        assertEquals("v1", contract.path("info").path("version").asString());
        assertFalse(paths.isEmpty());
        assertTrue(paths.stream().allMatch(path -> path.startsWith("/api/v1/")));
        assertTrue(paths.contains("/api/v1/inventory"));
        assertTrue(paths.contains("/api/v1/inventory/movements"));
        assertTrue(paths.contains("/api/v1/warehouses"));
        assertTrue(paths.contains("/api/v1/suppliers"));
        assertTrue(paths.contains("/api/v1/suppliers/{id}"));
        assertTrue(paths.contains("/api/v1/suppliers/{id}/products"));
        assertTrue(paths.contains(
                "/api/v1/suppliers/{id}/products/{productId}"));
        assertTrue(paths.contains("/api/v1/purchase-orders"));
        assertTrue(paths.contains("/api/v1/purchase-orders/{id}"));
        assertTrue(paths.contains("/api/v1/purchase-orders/{id}/items"));
        assertTrue(paths.contains("/api/v1/purchase-orders/{id}/issue"));
        assertTrue(paths.contains("/api/v1/purchase-orders/{id}/receipts"));
        assertTrue(paths.contains("/api/v1/purchase-orders/{id}/cancel"));
        assertTrue(paths.contains("/api/v1/inventory-transfers"));
        assertTrue(paths.contains("/api/v1/inventory-transfers/{id}"));
        assertTrue(paths.contains("/api/v1/inventory-transfers/{id}/items"));
        assertTrue(paths.contains("/api/v1/inventory-transfers/{id}/dispatch"));
        assertTrue(paths.contains("/api/v1/inventory-transfers/{id}/receive"));
        assertTrue(paths.contains("/api/v1/inventory-transfers/{id}/cancel"));
        assertTrue(paths.contains("/api/v1/inventory-counts"));
        assertTrue(paths.contains("/api/v1/inventory-counts/{id}"));
        assertTrue(paths.contains("/api/v1/inventory-counts/{id}/open"));
        assertTrue(paths.contains(
                "/api/v1/inventory-counts/{id}/lines/{productId}"));
        assertTrue(paths.contains("/api/v1/inventory-counts/{id}/submit"));
        assertTrue(paths.contains("/api/v1/inventory-counts/{id}/post"));
        assertTrue(paths.contains("/api/v1/inventory-counts/{id}/cancel"));
        assertTrue(paths.contains("/api/v1/warehouses/{warehouseId}/inventory"));
        assertTrue(paths.contains(
                "/api/v1/warehouses/{warehouseId}/inventory/{productId}/adjustments"));
        assertTrue(paths.contains(
                "/api/v1/warehouses/{warehouseId}/inventory/movements"));
        assertTrue(paths.contains(
                "/api/v1/warehouses/{warehouseId}/inventory/low-stock"));
        assertTrue(paths.contains(
                "/api/v1/warehouses/{warehouseId}/inventory/settings"));
        assertTrue(paths.contains(
                "/api/v1/warehouses/{warehouseId}/inventory/{productId}/settings"));

        Path output = Path.of("target", "openapi", "inventory-api-v1.json");
        Files.createDirectories(output.getParent());
        String canonicalContract = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(contract) + System.lineSeparator();
        Files.writeString(output, canonicalContract, StandardCharsets.UTF_8);
    }
}
