package com.example.inventory.transfers;

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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory-transfers")
@Tag(name = "Inventory transfers",
        description = "Inter-warehouse inventory transfer operations")
public class InventoryTransferController {

    private final InventoryTransferService service;

    public InventoryTransferController(InventoryTransferService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List inventory transfers")
    public PageResponse<InventoryTransferResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) InventoryTransferStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID sourceWarehouseId,
            @RequestParam(required = false) UUID destinationWarehouseId,
            @RequestParam(required = false) String folio) {
        return service.findAll(page, size, status, from, to,
                sourceWarehouseId, destinationWarehouseId, folio);
    }

    @PostMapping
    @Operation(summary = "Create a draft inventory transfer")
    public ResponseEntity<InventoryTransferResponse> create(
            @Valid @RequestBody CreateInventoryTransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        InventoryTransferResponse response = service.create(
                request, jwt.getSubject());
        return ResponseEntity.created(URI.create(
                "/api/v1/inventory-transfers/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an inventory transfer")
    public InventoryTransferResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}/items")
    @Operation(summary = "Replace items or warehouses on a draft transfer")
    public InventoryTransferResponse replaceItems(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInventoryTransferItemsRequest request) {
        return service.replaceItems(id, request);
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Dispatch a draft inventory transfer")
    public InventoryTransferResponse dispatch(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.dispatch(id, jwt.getSubject());
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Receive an in-transit inventory transfer")
    public InventoryTransferResponse receive(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.receive(id, jwt.getSubject());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a draft inventory transfer")
    public InventoryTransferResponse cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, jwt.getSubject());
    }
}
