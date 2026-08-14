package com.example.inventory.suppliers;

import com.example.inventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Suppliers", description = "Supplier and sourcing management")
class SupplierController {

    private final SupplierService service;

    SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search suppliers")
    PageResponse<SupplierResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String fiscalIdentifier,
            @RequestParam(required = false) Boolean active) {
        return service.findAll(page, size, code, name, fiscalIdentifier, active);
    }

    @PostMapping
    @Operation(summary = "Create a supplier")
    ResponseEntity<SupplierResponse> create(
            @Valid @RequestBody SupplierRequest request) {
        SupplierResponse response = service.create(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/suppliers/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a supplier")
    SupplierResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a supplier")
    SupplierResponse update(@PathVariable UUID id,
                            @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a supplier")
    void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "List products associated with a supplier")
    PageResponse<SupplierProductResponse> findProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.findProducts(id, page, size);
    }

    @PutMapping("/{id}/products/{productId}")
    @Operation(summary = "Create or replace a supplier-product association")
    SupplierProductResponse putProduct(
            @PathVariable UUID id,
            @PathVariable UUID productId,
            @Valid @RequestBody SupplierProductRequest request) {
        return service.putProduct(id, productId, request);
    }

    @DeleteMapping("/{id}/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a supplier-product association")
    void deactivateProduct(@PathVariable UUID id, @PathVariable UUID productId) {
        service.deactivateProduct(id, productId);
    }
}
