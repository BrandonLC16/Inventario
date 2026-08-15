package com.example.inventory.counts;

import com.example.inventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory-counts")
@Tag(name = "Inventory counts",
        description = "Non-blocking physical inventory count operations")
public class InventoryCountController {

    private final InventoryCountService service;

    public InventoryCountController(InventoryCountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List physical inventory counts")
    public PageResponse<InventoryCountResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) InventoryCountStatus status,
            @RequestParam(required = false) InventoryCountScope scope,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String folio) {
        return service.findAll(
                page, size, status, scope, warehouseId, folio);
    }

    @PostMapping
    @Operation(summary = "Create a draft physical inventory count")
    public ResponseEntity<InventoryCountResponse> create(
            @Valid @RequestBody CreateInventoryCountRequest request) {
        InventoryCountResponse response = service.create(request);
        return ResponseEntity.created(URI.create(
                "/api/v1/inventory-counts/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a physical inventory count")
    public InventoryCountResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/open")
    @Operation(summary = "Open a physical inventory count")
    public InventoryCountResponse open(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.open(id, jwt.getSubject());
    }

    @PutMapping("/{id}/lines/{productId}")
    @Operation(summary = "Capture a counted product quantity")
    public InventoryCountResponse updateLine(
            @PathVariable UUID id,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateInventoryCountLineRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.updateLine(
                id, productId, request, jwt.getSubject());
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a completed physical inventory count")
    public InventoryCountResponse submit(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.submit(id, jwt.getSubject());
    }

    @PostMapping("/{id}/post")
    @Operation(summary = "Post physical inventory count adjustments")
    public InventoryCountResponse post(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.post(id, jwt.getSubject());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an unposted physical inventory count")
    public InventoryCountResponse cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, jwt.getSubject());
    }
}
