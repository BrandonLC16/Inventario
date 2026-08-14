package com.example.inventory;

import com.example.inventory.users.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupplierIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "supplier-password-123";

    private String managerToken;
    private String salesToken;

    @BeforeEach
    void authenticateActors() throws Exception {
        createUser("supplier-manager", PASSWORD, true, false,
                RoleName.INVENTORY_MANAGER);
        createUser("supplier-sales", PASSWORD, true, false, RoleName.SALES);
        managerToken = login("supplier-manager", PASSWORD);
        salesToken = login("supplier-sales", PASSWORD);
    }

    @Test
    void suppliersSupportNormalizationFiltersPaginationAndLogicalDeactivation()
            throws Exception {
        String location = performManager(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson(
                                "  sup-001  ", "  Proveedora Legal Uno  ",
                                "  Comercial del Centro  ", "  mx-rfc-001  ",
                                "COMPRAS@EXAMPLE.COM", "  +52 55 1234  ", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUP-001"))
                .andExpect(jsonPath("$.legalName").value("Proveedora Legal Uno"))
                .andExpect(jsonPath("$.commercialName").value("Comercial del Centro"))
                .andExpect(jsonPath("$.fiscalIdentifier").value("MX-RFC-001"))
                .andExpect(jsonPath("$.email").value("compras@example.com"))
                .andExpect(jsonPath("$.phone").value("+52 55 1234"))
                .andReturn().getResponse().getHeader("Location");
        UUID supplierId = idFromLocation(location);

        createSupplier("SUP-002", "Otro proveedor", "Sucursal Norte",
                "MX-RFC-002", "otro@example.com", true);

        performManager(get("/api/v1/suppliers")
                        .param("page", "0")
                        .param("size", "1")
                        .param("code", "sup-00")
                        .param("name", "centro")
                        .param("fiscalIdentifier", "rfc-001")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(supplierId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));

        performManager(put("/api/v1/suppliers/{id}", supplierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson(
                                "SUP-001", "Proveedora Legal Actualizada",
                                null, "MX-RFC-001", "compras@example.com",
                                null, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName")
                        .value("Proveedora Legal Actualizada"))
                .andExpect(jsonPath("$.commercialName").doesNotExist());

        performManager(delete("/api/v1/suppliers/{id}", supplierId))
                .andExpect(status().isNoContent());
        performManager(get("/api/v1/suppliers/{id}", supplierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        performManager(get("/api/v1/suppliers")
                        .param("name", "actualizada")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        performSales(get("/api/v1/suppliers"))
                .andExpect(status().isForbidden());
    }

    @Test
    void supplierCodeFiscalIdentifierAndEmailAreUniqueWhenPresent()
            throws Exception {
        createSupplier("UNIQUE-1", "Proveedor uno", null,
                "RFC-UNIQUE", "unique@example.com", true);

        performManager(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson(
                                "unique-1", "Código duplicado", null,
                                null, null, null, true)))
                .andExpect(status().isConflict());
        performManager(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson(
                                "UNIQUE-2", "Fiscal duplicado", null,
                                "rfc-unique", null, null, true)))
                .andExpect(status().isConflict());
        performManager(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson(
                                "UNIQUE-3", "Correo duplicado", null,
                                null, "UNIQUE@EXAMPLE.COM", null, true)))
                .andExpect(status().isConflict());

        createSupplier("OPTIONAL-1", "Opcionales uno", null,
                null, null, true);
        createSupplier("OPTIONAL-2", "Opcionales dos", null,
                null, null, true);
    }

    @Test
    void supplierProductsArePagedLogicallyDeactivatedAndHaveOnePreferredSupplier()
            throws Exception {
        UUID firstSupplier = createSupplier(
                "SOURCE-1", "Proveedor preferido", null,
                null, null, true);
        UUID secondSupplier = createSupplier(
                "SOURCE-2", "Proveedor alterno", null,
                null, null, true);
        UUID productId = createProduct("SOURCE-PRODUCT");

        performManager(put("/api/v1/suppliers/{id}/products/{productId}",
                        firstSupplier, productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productAssociationJson(
                                "FIRST-SKU", 3, 5, "12.3456", true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierId").value(firstSupplier.toString()))
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.lastUnitCost").value(12.3456))
                .andExpect(jsonPath("$.preferred").value(true));

        performManager(put("/api/v1/suppliers/{id}/products/{productId}",
                        secondSupplier, productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productAssociationJson(
                                "SECOND-SKU", 7, 2, null, true, true)))
                .andExpect(status().isConflict());

        performManager(put("/api/v1/suppliers/{id}/products/{productId}",
                        secondSupplier, productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productAssociationJson(
                                "SECOND-SKU", 7, 2, null, true, false)))
                .andExpect(status().isBadRequest());

        performManager(put("/api/v1/suppliers/{id}/products/{productId}",
                        secondSupplier, productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productAssociationJson(
                                "SECOND-SKU", 7, 2, null, false, true)))
                .andExpect(status().isOk());
        performManager(get("/api/v1/suppliers/{id}/products", secondSupplier)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].supplierSku").value("SECOND-SKU"))
                .andExpect(jsonPath("$.totalElements").value(1));

        performManager(delete("/api/v1/suppliers/{id}", firstSupplier))
                .andExpect(status().isNoContent());
        performManager(put("/api/v1/suppliers/{id}/products/{productId}",
                        secondSupplier, productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productAssociationJson(
                                "SECOND-SKU", 7, 2, null, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferred").value(true));

        performManager(delete("/api/v1/suppliers/{id}/products/{productId}",
                        secondSupplier, productId))
                .andExpect(status().isNoContent());
        performManager(get("/api/v1/suppliers/{id}/products", secondSupplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].active").value(false))
                .andExpect(jsonPath("$.content[0].preferred").value(false));
    }

    private ResultActions performManager(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + managerToken));
    }

    private ResultActions performSales(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + salesToken));
    }

    private UUID createSupplier(String code, String legalName, String commercialName,
                                String fiscalIdentifier, String email, boolean active)
            throws Exception {
        String location = performManager(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson(code, legalName, commercialName,
                                fiscalIdentifier, email, null, active)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private UUID createProduct(String sku) throws Exception {
        String location = performManager(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"Supplier test product",
                                 "price":20.00,"active":true,"minimumStock":0}
                                """.formatted(sku)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private String supplierJson(String code, String legalName, String commercialName,
                                String fiscalIdentifier, String email, String phone,
                                boolean active) {
        return """
                {"code":%s,"legalName":%s,"commercialName":%s,
                 "fiscalIdentifier":%s,"email":%s,"phone":%s,"active":%s}
                """.formatted(json(code), json(legalName), json(commercialName),
                json(fiscalIdentifier), json(email), json(phone), active);
    }

    private String productAssociationJson(
            String supplierSku, int leadTimeDays, int minimumOrderQuantity,
            String lastUnitCost, boolean preferred, boolean active) {
        return """
                {"supplierSku":%s,"leadTimeDays":%d,"minimumOrderQuantity":%d,
                 "lastUnitCost":%s,"preferred":%s,"active":%s}
                """.formatted(json(supplierSku), leadTimeDays, minimumOrderQuantity,
                lastUnitCost == null ? "null" : lastUnitCost, preferred, active);
    }

    private String json(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
