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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseOrderIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "purchases-password-123";
    private static final UUID MAIN_WAREHOUSE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    private String managerToken;
    private String salesToken;

    @BeforeEach
    void authenticateActors() throws Exception {
        createUser("purchases-manager", PASSWORD, true, false,
                RoleName.INVENTORY_MANAGER);
        createUser("purchases-sales", PASSWORD, true, false, RoleName.SALES);
        managerToken = login("purchases-manager", PASSWORD);
        salesToken = login("purchases-sales", PASSWORD);
    }

    @Test
    void supportsDraftIssuePartialReceiptsHistoricalCostsAndIdempotency()
            throws Exception {
        UUID supplierId = createSupplier("PURCHASE-SUP-1");
        UUID productId = createProduct("PURCHASE-PRODUCT-1", "99.99");
        associateSupplierProduct(supplierId, productId, "SOURCE-SKU", "1.0000");

        String created = performManager(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseOrderJson(supplierId,
                                new RequestedLine(productId, "SOURCE-SKU", 5, "10.0000"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.total").value(50.0))
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(created, "$.id"));
        UUID itemId = UUID.fromString(JsonPath.read(created, "$.items[0].id"));

        performManager(put("/api/v1/purchase-orders/{id}/items", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateItemsJson(
                                new RequestedLine(productId, "HISTORICAL-SKU", 6,
                                        "11.0000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.items[0].supplierSku")
                        .value("HISTORICAL-SKU"))
                .andExpect(jsonPath("$.total").value(66.0));

        performManager(post("/api/v1/purchase-orders/{id}/issue", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty());
        performManager(put("/api/v1/purchase-orders/{id}/items", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateItemsJson(
                                new RequestedLine(productId, null, 8, "9.0000"))))
                .andExpect(status().isConflict());

        String firstReceipt = receiptJson(
                "SUPPLIER-DOC-1", true,
                new ReceiptLine(itemId, 2, "12.5000"));
        String receiptResponse = performManager(
                post("/api/v1/purchase-orders/{id}/receipts", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstReceipt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalReference")
                        .value("SUPPLIER-DOC-1"))
                .andExpect(jsonPath("$.updateSupplierProductLastCost")
                        .value(true))
                .andExpect(jsonPath("$.items[0].unitCost").value(12.5))
                .andReturn().getResponse().getContentAsString();
        UUID firstReceiptId = UUID.fromString(
                JsonPath.read(receiptResponse, "$.id"));

        performManager(get("/api/v1/purchase-orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.items[0].receivedQuantity").value(2))
                .andExpect(jsonPath("$.items[0].pendingQuantity").value(4))
                .andExpect(jsonPath("$.items[0].unitCost").value(11.0));
        assertEquals(2, stock(productId));
        assertEquals(1, purchaseMovementCount(firstReceiptId));
        assertEquals("12.5000", supplierLastCost(supplierId, productId));
        assertEquals("99.99", productPrice(productId));

        performManager(post("/api/v1/purchase-orders/{id}/receipts", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstReceipt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstReceiptId.toString()));
        assertEquals(2, stock(productId));
        assertEquals(1, purchaseMovementCount(firstReceiptId));

        jdbcTemplate.update("""
                UPDATE purchase_receipts
                SET update_supplier_product_last_cost = NULL
                WHERE id = ?
                """, firstReceiptId);
        performManager(post("/api/v1/purchase-orders/{id}/receipts", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstReceipt))
                .andExpect(status().isConflict());
        jdbcTemplate.update("""
                UPDATE purchase_receipts
                SET update_supplier_product_last_cost = TRUE
                WHERE id = ?
                """, firstReceiptId);

        performManager(post("/api/v1/purchase-orders/{id}/receipts", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptJson("SUPPLIER-DOC-1", false,
                                new ReceiptLine(itemId, 2, "12.5000"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "External reference SUPPLIER-DOC-1 was already used with different receipt content"));
        assertEquals(2, stock(productId));
        assertEquals(1, purchaseMovementCount(firstReceiptId));

        performManager(post("/api/v1/purchase-orders/{id}/receipts", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptJson("SUPPLIER-DOC-1", false,
                                new ReceiptLine(itemId, 1, "12.5000"))))
                .andExpect(status().isConflict());

        performManager(post("/api/v1/purchase-orders/{id}/receipts", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptJson("SUPPLIER-DOC-2", false,
                                new ReceiptLine(itemId, 4, "13.7500"))))
                .andExpect(status().isCreated());
        performManager(get("/api/v1/purchase-orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.items[0].receivedQuantity").value(6))
                .andExpect(jsonPath("$.items[0].pendingQuantity").value(0));
        performManager(get("/api/v1/purchase-orders/{id}/receipts", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].unitCost").value(12.5))
                .andExpect(jsonPath("$[1].items[0].unitCost").value(13.75));
        assertEquals(6, stock(productId));
        assertEquals("12.5000", supplierLastCost(supplierId, productId));
        assertEquals("99.99", productPrice(productId));

        performManager(post("/api/v1/purchase-orders/{id}/cancel", orderId))
                .andExpect(status().isConflict());
    }

    @Test
    void receivingMoreThanPendingRollsBackTheWholeReceipt() throws Exception {
        UUID supplierId = createSupplier("PURCHASE-SUP-2");
        UUID firstProduct = createProduct("PURCHASE-ROLLBACK-1", "20.00");
        UUID secondProduct = createProduct("PURCHASE-ROLLBACK-2", "30.00");
        String created = createPurchaseOrder(supplierId,
                new RequestedLine(firstProduct, null, 2, "5.0000"),
                new RequestedLine(secondProduct, null, 2, "6.0000"));
        UUID orderId = UUID.fromString(JsonPath.read(created, "$.id"));
        List<String> itemIds = JsonPath.read(created, "$.items[*].id");
        UUID firstItem = UUID.fromString(itemIds.get(0));
        UUID secondItem = UUID.fromString(itemIds.get(1));
        performManager(post("/api/v1/purchase-orders/{id}/issue", orderId))
                .andExpect(status().isOk());

        performManager(post("/api/v1/purchase-orders/{id}/receipts", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptJson("OVER-PENDING", false,
                                new ReceiptLine(firstItem, 2, "5.0000"),
                                new ReceiptLine(secondItem, 3, "6.0000"))))
                .andExpect(status().isBadRequest());

        assertEquals(0, stock(firstProduct));
        assertEquals(0, stock(secondProduct));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_receipts WHERE purchase_order_id = ?",
                Integer.class, orderId));
        assertEquals("ISSUED", orderStatus(orderId));
    }

    @Test
    void creationRejectsQuantityAboveThePurchaseTotalLimitAsBadRequest()
            throws Exception {
        UUID supplierId = createSupplier("PURCHASE-LIMIT-SUP");
        UUID productId = createProduct("PURCHASE-LIMIT-PRODUCT", "1.00");

        performManager(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseOrderJson(supplierId,
                                new RequestedLine(productId, null, 10_001,
                                        "1.0000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath(
                        "$.validationErrors['items[0].orderedQuantity']")
                        .value("must be less than or equal to 10000"));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_orders", Integer.class));
    }

    @Test
    void draftAndUnreceivedIssuedOrdersCanBeCancelledAndSalesCannotAccessPurchases()
            throws Exception {
        UUID supplierId = createSupplier("PURCHASE-SUP-3");
        UUID productId = createProduct("PURCHASE-CANCEL-1", "40.00");

        String draft = createPurchaseOrder(supplierId,
                new RequestedLine(productId, null, 1, "10.0000"));
        UUID draftId = UUID.fromString(JsonPath.read(draft, "$.id"));
        performManager(post("/api/v1/purchase-orders/{id}/cancel", draftId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.issuedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        String issued = createPurchaseOrder(supplierId,
                new RequestedLine(productId, null, 1, "10.0000"));
        UUID issuedId = UUID.fromString(JsonPath.read(issued, "$.id"));
        performManager(post("/api/v1/purchase-orders/{id}/issue", issuedId))
                .andExpect(status().isOk());
        performManager(post("/api/v1/purchase-orders/{id}/cancel", issuedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/purchase-orders")
                        .header(AUTHORIZATION, "Bearer " + salesToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentReceiptsCannotExceedTheOrderPendingQuantity() throws Exception {
        UUID supplierId = createSupplier("PURCHASE-SUP-4");
        UUID productId = createProduct("PURCHASE-CONCURRENT-1", "50.00");
        String created = createPurchaseOrder(supplierId,
                new RequestedLine(productId, null, 5, "10.0000"));
        UUID orderId = UUID.fromString(JsonPath.read(created, "$.id"));
        UUID itemId = UUID.fromString(JsonPath.read(created, "$.items[0].id"));
        performManager(post("/api/v1/purchase-orders/{id}/issue", orderId))
                .andExpect(status().isOk());

        List<Integer> statuses = concurrentlyReceive(orderId,
                receiptJson("CONCURRENT-RECEIPT-1", false,
                        new ReceiptLine(itemId, 4, "10.0000")),
                receiptJson("CONCURRENT-RECEIPT-2", false,
                        new ReceiptLine(itemId, 4, "10.0000")));

        assertEquals(List.of(201, 400), statuses);
        assertEquals(4, stock(productId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_receipts WHERE purchase_order_id = ?",
                Integer.class, orderId));
        assertEquals("PARTIALLY_RECEIVED", orderStatus(orderId));
    }

    private List<Integer> concurrentlyReceive(
            UUID orderId, String firstBody, String secondBody) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Integer> first = executor.submit(
                    concurrentReceipt(orderId, firstBody, barrier));
            Future<Integer> second = executor.submit(
                    concurrentReceipt(orderId, secondBody, barrier));
            return Arrays.asList(
                            first.get(30, TimeUnit.SECONDS),
                            second.get(30, TimeUnit.SECONDS))
                    .stream().sorted().toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> concurrentReceipt(
            UUID orderId, String body, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(
                            post("/api/v1/purchase-orders/{id}/receipts", orderId)
                                    .header(AUTHORIZATION, "Bearer " + managerToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andReturn().getResponse().getStatus();
        };
    }

    private String createPurchaseOrder(
            UUID supplierId, RequestedLine... lines) throws Exception {
        return performManager(post("/api/v1/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseOrderJson(supplierId, lines)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID createSupplier(String code) throws Exception {
        String location = performManager(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","legalName":"%s", "active":true}
                                """.formatted(code, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private UUID createProduct(String sku, String price) throws Exception {
        String location = performManager(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":%s,"active":true}
                                """.formatted(sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return idFromLocation(location);
    }

    private void associateSupplierProduct(UUID supplierId, UUID productId,
                                          String supplierSku, String lastCost)
            throws Exception {
        performManager(put("/api/v1/suppliers/{id}/products/{productId}",
                        supplierId, productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierSku":"%s","leadTimeDays":1,
                                 "minimumOrderQuantity":1,"lastUnitCost":%s,
                                 "preferred":false,"active":true}
                                """.formatted(supplierSku, lastCost)))
                .andExpect(status().isOk());
    }

    private String purchaseOrderJson(UUID supplierId, RequestedLine... lines) {
        return """
                {"supplierId":"%s","destinationWarehouseId":"%s",
                 "currency":"mxn","supplierReference":" SUPPLIER-PO ",
                 "items":[%s]}
                """.formatted(supplierId, MAIN_WAREHOUSE_ID,
                Arrays.stream(lines).map(line -> """
                        {"productId":"%s","supplierSku":%s,
                         "orderedQuantity":%d,"unitCost":%s}
                        """.formatted(line.productId(), jsonString(line.supplierSku()),
                        line.quantity(), line.unitCost()))
                        .collect(java.util.stream.Collectors.joining(",")));
    }

    private String updateItemsJson(RequestedLine... lines) {
        return "{\"items\":[" + Arrays.stream(lines).map(line -> """
                {"productId":"%s","supplierSku":%s,
                 "orderedQuantity":%d,"unitCost":%s}
                """.formatted(line.productId(), jsonString(line.supplierSku()),
                line.quantity(), line.unitCost()))
                .collect(java.util.stream.Collectors.joining(",")) + "]}";
    }

    private String receiptJson(String reference, boolean updateCost,
                               ReceiptLine... lines) {
        return """
                {"externalReference":"%s","updateSupplierProductLastCost":%s,
                 "items":[%s]}
                """.formatted(reference, updateCost,
                Arrays.stream(lines).map(line -> """
                        {"purchaseOrderItemId":"%s","quantity":%d,"unitCost":%s}
                        """.formatted(line.itemId(), line.quantity(), line.unitCost()))
                        .collect(java.util.stream.Collectors.joining(",")));
    }

    private ResultActions performManager(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(
                AUTHORIZATION, "Bearer " + managerToken));
    }

    private UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private int stock(UUID productId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE((SELECT quantity FROM inventory
                    WHERE warehouse_id = ? AND product_id = ?), 0)
                """, Integer.class, MAIN_WAREHOUSE_ID, productId);
    }

    private int purchaseMovementCount(UUID receiptId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_movements
                WHERE movement_type = 'PURCHASE_RECEIVED'
                  AND business_reference = ?
                """, Integer.class, receiptId.toString());
    }

    private String supplierLastCost(UUID supplierId, UUID productId) {
        return jdbcTemplate.queryForObject("""
                SELECT last_unit_cost::text FROM supplier_products
                WHERE supplier_id = ? AND product_id = ?
                """, String.class, supplierId, productId);
    }

    private String productPrice(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT price::text FROM products WHERE id = ?",
                String.class, productId);
    }

    private String orderStatus(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM purchase_orders WHERE id = ?",
                String.class, orderId);
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private record RequestedLine(
            UUID productId, String supplierSku, int quantity, String unitCost) {
    }

    private record ReceiptLine(UUID itemId, int quantity, String unitCost) {
    }
}
