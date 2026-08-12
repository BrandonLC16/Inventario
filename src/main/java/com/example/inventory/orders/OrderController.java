package com.example.inventory.orders;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order creation and lifecycle operations")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List orders")
    public List<OrderResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order")
    public OrderResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a pending order")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm an order")
    public OrderResponse confirm(@PathVariable UUID id,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.confirm(id, jwt.getSubject());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a confirmed order")
    public OrderResponse cancel(@PathVariable UUID id,
                                @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, jwt.getSubject());
    }
}
