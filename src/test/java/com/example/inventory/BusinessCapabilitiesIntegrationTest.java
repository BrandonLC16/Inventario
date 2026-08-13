package com.example.inventory;

import com.example.inventory.users.RoleName;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessCapabilitiesIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "business-password-123";

    private String adminToken;
    private String salesToken;
    private String managerToken;
    private UUID adminUserId;
    private UUID salesUserId;

    @BeforeEach
    void authenticateActors() throws Exception {
        adminUserId = createUser(
                "business-admin", PASSWORD, true, false, RoleName.ADMIN).getId();
        salesUserId = createUser(
                "business-sales", PASSWORD, true, false, RoleName.SALES).getId();
        createUser("business-manager", PASSWORD, true, false,
                RoleName.INVENTORY_MANAGER);
        adminToken = login("business-admin", PASSWORD);
        salesToken = login("business-sales", PASSWORD);
        managerToken = login("business-manager", PASSWORD);
    }

    @Test
    void customersSupportCrudUniquenessSearchDeactivationAndPermissions()
            throws Exception {
        String location = performSales(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson(
                                "Cliente Uno", "mx-rfc-100", "CLIENTE@EXAMPLE.COM", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fiscalIdentifier").value("MX-RFC-100"))
                .andExpect(jsonPath("$.email").value("cliente@example.com"))
                .andReturn().getResponse().getHeader("Location");
        UUID customerId = idFromLocation(location);

        performAdmin(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson(
                                "Duplicado", null, "cliente@example.com", true)))
                .andExpect(status().isConflict());

        performSales(get("/api/v1/customers")
                        .param("page", "0")
                        .param("size", "1")
                        .param("search", "rfc-100")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(customerId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        performSales(put("/api/v1/customers/{id}", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson(
                                "Cliente Actualizado", "MX-RFC-100",
                                "cliente@example.com", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cliente Actualizado"));
        performSales(delete("/api/v1/customers/{id}", customerId))
                .andExpect(status().isNoContent());
        performSales(get("/api/v1/customers")
                        .param("search", "actualizado")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].active").value(false));

        mockMvc.perform(get("/api/v1/customers")
                        .header(AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void productAndUserListingsArePagedAndApplyAllSupportedFilters()
            throws Exception {
        createProduct("FILTER-A", "Alpha filter", "10.00", true, 0);
        createProduct("FILTER-B", "Beta filter", "20.00", false, 0);
        createProduct("OTHER-C", "Gamma", "30.00", true, 0);

        mockMvc.perform(get("/api/v1/products")
                        .header(AUTHORIZATION, "Bearer " + salesToken)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sku", "filter")
                        .param("name", "alpha")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].sku").value("FILTER-A"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));

        createUser("warehouse.person", PASSWORD, true, false,
                RoleName.INVENTORY_MANAGER);
        performAdmin(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "1")
                        .param("username", "warehouse")
                        .param("email", "example.com")
                        .param("role", "INVENTORY_MANAGER")
                        .param("enabled", "true")
                        .param("locked", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username").value("warehouse.person"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void ordersKeepHistoricalPricesTotalsCustomerFolioAndLifecycleActors()
            throws Exception {
        UUID customerId = createCustomer(
                "Comprador Histórico", "RFC-HISTORY", "history@example.com");
        UUID productId = createProduct(
                "PRICE-HISTORY", "Historical product", "25.50", true, 0);
        adjust(productId, 10, "RECEIPT-HISTORY");

        String created = performSales(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","items":[
                                  {"productId":"%s","quantity":2}
                                ]}
                                """.formatted(customerId, productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folio", startsWith("ORD-")))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.total").value(51.0))
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.5))
                .andExpect(jsonPath("$.items[0].subtotal").value(51.0))
                .andExpect(jsonPath("$.createdBy").value(salesUserId.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(created, "$.id"));
        String folio = JsonPath.read(created, "$.folio");

        performAdmin(put("/api/v1/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(
                                "PRICE-HISTORY", "Historical product",
                                "99.00", true, 0)))
                .andExpect(status().isOk());
        performSales(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(51.0))
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.5));

        performSales(post("/api/v1/orders/{id}/reserve", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedBy").value(salesUserId.toString()))
                .andExpect(jsonPath("$.reservedAt").isNotEmpty());
        performSales(post("/api/v1/orders/{id}/confirm", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedBy").value(salesUserId.toString()))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());
        performSales(post("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledBy").value(salesUserId.toString()))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        performSales(get("/api/v1/orders")
                        .param("page", "0")
                        .param("size", "1")
                        .param("status", "CANCELLED")
                        .param("customerId", customerId.toString())
                        .param("folio", folio)
                        .param("from", "2000-01-01T00:00:00Z")
                        .param("to", "2100-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pendingOrderCanBeRepricedAndDeletedWithoutReservingOrMovingStock()
            throws Exception {
        UUID firstProduct = createProduct(
                "PENDING-A", "Pending A", "10.00", true, 0);
        UUID secondProduct = createProduct(
                "PENDING-B", "Pending B", "7.00", true, 0);
        adjust(firstProduct, 5, "RECEIPT-PENDING");

        String location = performSales(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":2}]}
                                """.formatted(firstProduct)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        UUID orderId = idFromLocation(location);
        assertEquals(5, stock(firstProduct));

        performSales(put("/api/v1/orders/{id}/items", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":3}]}
                                """.formatted(secondProduct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(21.0))
                .andExpect(jsonPath("$.items[0].productId")
                        .value(secondProduct.toString()));
        assertEquals(5, stock(firstProduct));
        assertEquals(0, stock(secondProduct));

        performSales(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNoContent());
        performSales(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM stock_movements
                WHERE business_reference = ?
                  AND movement_type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED')
                """, Integer.class, orderId.toString()));
    }

    @Test
    void lowStockAlertsAndReceivingReferencesAreExposedOperationally()
            throws Exception {
        UUID productId = createProduct(
                "LOW-STOCK", "Low stock product", "5.00", true, 5);

        performAdmin(get("/api/v1/inventory/low-stock")
                        .param("search", "LOW-STOCK")
                        .param("outOfStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].productId")
                        .value(productId.toString()))
                .andExpect(jsonPath("$.content[0].quantity").value(0))
                .andExpect(jsonPath("$.content[0].minimumStock").value(5))
                .andExpect(jsonPath("$.content[0].replenishmentQuantity").value(5))
                .andExpect(jsonPath("$.content[0].alert").value("OUT_OF_STOCK"));

        adjust(productId, 3, "PURCHASE-RECEIPT-42");
        performAdmin(get("/api/v1/inventory/low-stock")
                        .param("search", "low stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].quantity").value(3))
                .andExpect(jsonPath("$.content[0].replenishmentQuantity").value(2))
                .andExpect(jsonPath("$.content[0].alert").value("LOW_STOCK"));
        performAdmin(get("/api/v1/inventory/{id}/movements", productId)
                        .param("reference", "PURCHASE-RECEIPT-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].businessReference")
                        .value("PURCHASE-RECEIPT-42"))
                .andExpect(jsonPath("$.content[0].responsibleUser")
                        .value(adminUserId.toString()));
    }

    private ResultActions performAdmin(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + adminToken));
    }

    private ResultActions performSales(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + salesToken));
    }

    private UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private int stock(UUID productId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE((
                    SELECT quantity FROM inventory WHERE product_id = ?
                ), 0)
                """, Integer.class, productId);
    }

    private UUID createProduct(String sku, String name, String price,
                               boolean active, int minimumStock) throws Exception {
        String location = performAdmin(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, name, price, active, minimumStock)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private UUID createCustomer(String name, String fiscalIdentifier, String email)
            throws Exception {
        String location = performSales(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson(name, fiscalIdentifier, email, true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private void adjust(UUID productId, int delta, String reference) throws Exception {
        performAdmin(patch("/api/v1/inventory/{id}/adjustments", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":%d,"reference":"%s"}
                                """.formatted(delta, reference)))
                .andExpect(status().isOk());
    }

    private String productJson(String sku, String name, String price,
                               boolean active, int minimumStock) {
        return """
                {"sku":"%s","name":"%s","description":"Integration product",
                 "price":%s,"active":%s,"minimumStock":%d}
                """.formatted(sku, name, price, active, minimumStock);
    }

    private String customerJson(String name, String fiscalIdentifier,
                                String email, boolean active) {
        if (fiscalIdentifier == null) {
            return """
                    {"name":"%s","email":"%s","active":%s}
                    """.formatted(name, email, active);
        }
        return """
                {"name":"%s","fiscalIdentifier":"%s",
                 "email":"%s","active":%s}
                """.formatted(name, fiscalIdentifier, email, active);
    }
}
