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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import com.example.inventory.shared.PageResponse;
import java.time.Instant;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order creation and lifecycle operations")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List orders")
    public PageResponse<OrderResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String folio) {
        return service.findAll(page, size, status, from, to, customerId, folio);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order")
    public OrderResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a pending order")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        OrderResponse response = service.create(request, jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm an order")
    public OrderResponse confirm(@PathVariable UUID id,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.confirm(id, jwt.getSubject());
    }

    @PostMapping("/{id}/reserve")
    @Operation(summary = "Reserve inventory for a pending order")
    public OrderResponse reserve(@PathVariable UUID id,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.reserve(id, jwt.getSubject());
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Release inventory for a reserved order")
    public OrderResponse release(@PathVariable UUID id,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.release(id, jwt.getSubject());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a confirmed order")
    public OrderResponse cancel(@PathVariable UUID id,
                                @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, jwt.getSubject());
    }

    @PutMapping("/{id}/items")
    @Operation(summary = "Replace items, releasing a reservation when present")
    public OrderResponse replaceItems(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderItemsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.replaceItems(id, request, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a pending or reserved order")
    public void deletePending(@PathVariable UUID id,
                              @AuthenticationPrincipal Jwt jwt) {
        service.deletePending(id, jwt.getSubject());
    }
}
