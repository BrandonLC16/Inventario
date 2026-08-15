package com.example.inventory.purchases;

import com.example.inventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "Purchase orders",
        description = "Purchase order issuing and receiving operations")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List purchase orders")
    public PageResponse<PurchaseOrderResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID destinationWarehouseId,
            @RequestParam(required = false) String folio) {
        return service.findAll(page, size, status, from, to, supplierId,
                destinationWarehouseId, folio);
    }

    @PostMapping
    @Operation(summary = "Create a draft purchase order")
    public ResponseEntity<PurchaseOrderResponse> create(
            @Valid @RequestBody CreatePurchaseOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        PurchaseOrderResponse response = service.create(request, jwt.getSubject());
        return ResponseEntity.created(URI.create(
                "/api/v1/purchase-orders/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a purchase order")
    public PurchaseOrderResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}/items")
    @Operation(summary = "Replace items or destination on a draft purchase order")
    public PurchaseOrderResponse replaceItems(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePurchaseOrderItemsRequest request) {
        return service.replaceItems(id, request);
    }

    @PostMapping("/{id}/issue")
    @Operation(summary = "Issue a draft purchase order")
    public PurchaseOrderResponse issue(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.issue(id, jwt.getSubject());
    }

    @PostMapping("/{id}/receipts")
    @Operation(summary = "Receive products for an issued purchase order")
    public ResponseEntity<PurchaseReceiptResponse> receive(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePurchaseReceiptRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        PurchaseReceiptResult result = service.receive(
                id, request, jwt.getSubject());
        if (!result.created()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity.created(URI.create(
                "/api/v1/purchase-orders/" + id + "/receipts/"
                        + result.response().id())).body(result.response());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a purchase order without receipts")
    public PurchaseOrderResponse cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, jwt.getSubject());
    }

    @GetMapping("/{id}/receipts")
    @Operation(summary = "List receipts for a purchase order")
    public List<PurchaseReceiptResponse> findReceipts(@PathVariable UUID id) {
        return service.findReceipts(id);
    }
}
