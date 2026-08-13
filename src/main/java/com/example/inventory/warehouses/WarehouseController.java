package com.example.inventory.warehouses;

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
@RequestMapping("/api/v1/warehouses")
@Tag(name = "Warehouses", description = "Warehouse directory")
class WarehouseController {
    private final WarehouseService service;

    WarehouseController(WarehouseService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List warehouses")
    PageResponse<WarehouseResponse> findAll(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.findAll(page, size);
    }

    @PostMapping
    @Operation(summary = "Create a warehouse")
    ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/warehouses/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a warehouse")
    WarehouseResponse findById(@PathVariable UUID id) { return service.findById(id); }

    @PutMapping("/{id}")
    @Operation(summary = "Update a warehouse")
    WarehouseResponse update(@PathVariable UUID id, @Valid @RequestBody WarehouseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a warehouse")
    void deactivate(@PathVariable UUID id) { service.deactivate(id); }
}
