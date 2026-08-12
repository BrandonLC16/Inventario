package com.example.inventory.orders;

import com.example.inventory.inventory.InventoryOperations;
import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orders;
    private final ProductCatalog products;
    private final InventoryOperations inventory;

    public OrderService(OrderRepository orders, ProductCatalog products,
                        InventoryOperations inventory) {
        this.orders = orders;
        this.products = products;
        this.inventory = inventory;
    }

    public List<OrderResponse> findAll() {
        return orders.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderResponse::from)
                .toList();
    }

    public OrderResponse findById(UUID id) {
        return OrderResponse.from(orders.findDetailedById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        List<CreateOrderItemRequest> items = validateItems(request);
        items.stream().map(CreateOrderItemRequest::productId)
                .sorted().forEach(products::requireProduct);
        return OrderResponse.from(orders.saveAndFlush(new SalesOrder(items)));
    }

    @Transactional
    public OrderResponse confirm(UUID id, String responsibleUser) {
        SalesOrder order = requireForUpdate(id);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("A cancelled order cannot be confirmed");
        }
        String actor = requireActor(responsibleUser);
        sortedItems(order).forEach(item -> inventory.consumeForOrder(
                item.getProductId(), item.getQuantity(), order.getId(), actor));
        order.confirm();
        orders.flush();
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UUID id, String responsibleUser) {
        SalesOrder order = requireForUpdate(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            throw new ConflictException("A pending order cannot be cancelled");
        }
        String actor = requireActor(responsibleUser);
        sortedItems(order).forEach(item -> inventory.restoreForOrder(
                item.getProductId(), item.getQuantity(), order.getId(), actor));
        order.cancel();
        orders.flush();
        return OrderResponse.from(order);
    }

    private List<CreateOrderItemRequest> validateItems(CreateOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("An order must contain at least one item");
        }
        if (request.items().size() > 100) {
            throw new BadRequestException("An order cannot contain more than 100 items");
        }
        Set<UUID> productIds = new HashSet<>();
        for (CreateOrderItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() <= 0) {
                throw new BadRequestException("Every order item requires a product and a positive quantity");
            }
            if (!productIds.add(item.productId())) {
                throw new BadRequestException("An order must not contain duplicate products");
            }
        }
        return List.copyOf(request.items());
    }

    private SalesOrder requireForUpdate(UUID id) {
        return orders.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private List<OrderItem> sortedItems(SalesOrder order) {
        return order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .toList();
    }

    private String requireActor(String responsibleUser) {
        if (responsibleUser == null || responsibleUser.isBlank()) {
            throw new IllegalArgumentException("The responsible user is required");
        }
        return responsibleUser.trim();
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException("Order %s was not found".formatted(id));
    }
}
