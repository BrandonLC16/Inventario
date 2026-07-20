package com.example.inventory.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Stock query and adjustment operations")
class InventoryController {

    private final InventoryService service;

    InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get stock for a product")
    InventoryResponse findByProductId(@PathVariable UUID productId) {
        return service.findByProductId(productId);
    }

    @PatchMapping("/{productId}/adjustments")
    @Operation(summary = "Adjust stock", description = "Applies an increment or decrement atomically")
    InventoryResponse adjust(@PathVariable UUID productId,
                             @Valid @RequestBody StockAdjustmentRequest request) {
        return service.adjust(productId, request.quantityDelta());
    }
}
