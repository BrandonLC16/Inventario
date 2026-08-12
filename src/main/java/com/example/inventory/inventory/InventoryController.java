package com.example.inventory.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;
import com.example.inventory.shared.PageResponse;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Stock query and adjustment operations")
class InventoryController {

    private final InventoryService service;
    private final StockMovementQueryService movementQueries;

    InventoryController(InventoryService service, StockMovementQueryService movementQueries) {
        this.service = service;
        this.movementQueries = movementQueries;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get stock for a product")
    InventoryResponse findByProductId(@PathVariable UUID productId) {
        return service.findByProductId(productId);
    }

    @GetMapping("/{productId}/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @Operation(summary = "Get the movement history for a product")
    StockMovementPageResponse findMovements(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String reference) {
        return movementQueries.findMovements(
                productId, page, size, type, from, to, reference);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @Operation(summary = "List low-stock and out-of-stock products")
    PageResponse<LowStockResponse> findLowStock(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean outOfStockOnly) {
        return service.findLowStock(page, size, search, outOfStockOnly);
    }

    @PatchMapping("/{productId}/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @Operation(summary = "Adjust stock", description = "Applies an increment or decrement atomically")
    InventoryResponse adjust(@PathVariable UUID productId,
                             @Valid @RequestBody StockAdjustmentRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return service.adjust(productId, request.quantityDelta(),
                request.reference(), jwt.getSubject());
    }
}
