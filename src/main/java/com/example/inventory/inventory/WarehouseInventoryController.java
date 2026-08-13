package com.example.inventory.inventory;

import com.example.inventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses/{warehouseId}/inventory")
@Tag(name = "Warehouse inventory", description = "Warehouse-scoped balances, movements and alerts")
class WarehouseInventoryController {
    private final InventoryService service;
    private final StockMovementQueryService movementQueries;

    WarehouseInventoryController(InventoryService service,
                                 StockMovementQueryService movementQueries) {
        this.service = service;
        this.movementQueries = movementQueries;
    }

    @GetMapping
    @Operation(summary = "List warehouse inventory balances")
    PageResponse<InventoryResponse> findAll(@PathVariable UUID warehouseId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.findAll(warehouseId, page, size);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get a warehouse balance for one product")
    InventoryResponse findByProductId(@PathVariable UUID warehouseId,
                                      @PathVariable UUID productId) {
        return service.findByProductId(warehouseId, productId);
    }

    @GetMapping("/settings")
    @Operation(summary = "List warehouse product settings")
    PageResponse<InventorySettingResponse> findSettings(
            @PathVariable UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.findSettings(warehouseId, page, size);
    }

    @GetMapping("/{productId}/settings")
    @Operation(summary = "Get warehouse settings for one product")
    InventorySettingResponse findSetting(@PathVariable UUID warehouseId,
                                         @PathVariable UUID productId) {
        return service.findSetting(warehouseId, productId);
    }

    @PatchMapping("/{productId}/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @Operation(summary = "Adjust warehouse stock atomically")
    InventoryResponse adjust(@PathVariable UUID warehouseId,
                             @PathVariable UUID productId,
                             @Valid @RequestBody StockAdjustmentRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return service.adjust(warehouseId, productId, request.quantityDelta(),
                request.reference(), jwt.getSubject());
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @Operation(summary = "List warehouse stock movements")
    StockMovementPageResponse findMovements(
            @PathVariable UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String reference) {
        return movementQueries.findMovements(warehouseId, productId, page, size,
                type, from, to, reference);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @Operation(summary = "List warehouse low-stock alerts")
    PageResponse<LowStockResponse> findLowStock(
            @PathVariable UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean outOfStockOnly) {
        return service.findLowStock(warehouseId, page, size, search, outOfStockOnly);
    }

    @PutMapping("/{productId}/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @Operation(summary = "Configure replenishment for a warehouse product")
    void configure(@PathVariable UUID warehouseId, @PathVariable UUID productId,
                   @Valid @RequestBody InventorySettingRequest request) {
        service.configureProduct(warehouseId, productId,
                request.minimumStock(), request.active());
    }
}
