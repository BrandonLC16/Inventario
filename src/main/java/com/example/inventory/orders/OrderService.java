package com.example.inventory.orders;

import com.example.inventory.inventory.InventoryOperations;
import com.example.inventory.products.ProductCatalog;
import com.example.inventory.products.ProductSnapshot;
import com.example.inventory.customers.CustomerDirectory;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import com.example.inventory.warehouses.WarehouseDirectory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import java.time.Instant;
import java.util.ArrayList;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orders;
    private final ProductCatalog products;
    private final InventoryOperations inventory;
    private final CustomerDirectory customers;
    private final WarehouseDirectory warehouses;

    public OrderService(OrderRepository orders, ProductCatalog products,
                        InventoryOperations inventory, CustomerDirectory customers,
                        WarehouseDirectory warehouses) {
        this.orders = orders;
        this.products = products;
        this.inventory = inventory;
        this.customers = customers;
        this.warehouses = warehouses;
    }

    public PageResponse<OrderResponse> findAll(int page, int size, OrderStatus status,
                                               Instant from, Instant to, UUID customerId,
                                               String folio) {
        PageSupport.validateDateRange(from, to);
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(orders.findAll(
                filters(status, from, to, customerId, folio), pageable),
                OrderResponse::from);
    }

    public OrderResponse findById(UUID id) {
        return OrderResponse.from(orders.findDetailedById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String responsibleUser) {
        List<CreateOrderItemRequest> items = validateItems(request);
        if (request.customerId() != null) {
            customers.requireActiveCustomer(request.customerId());
        }
        UUID warehouseId = request.fulfillmentWarehouseId() == null
                ? WarehouseDirectory.MAIN_WAREHOUSE_ID
                : request.fulfillmentWarehouseId();
        warehouses.lockActiveWarehouse(warehouseId);
        List<PricedOrderItem> pricedItems = priceItems(items);
        String folio = "ORD-%010d".formatted(orders.nextFolioSequence());
        SalesOrder order = new SalesOrder(
                folio, request.customerId(), warehouseId, "MXN",
                requireActor(responsibleUser), pricedItems);
        return OrderResponse.from(orders.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse replaceItems(UUID id, UpdateOrderItemsRequest request,
                                      String responsibleUser) {
        SalesOrder order = requireForUpdate(id);
        if (order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.RESERVED) {
            throw new ConflictException(
                    "Only a pending or reserved order can be updated");
        }
        UUID requestedWarehouseId = request.fulfillmentWarehouseId();
        if (requestedWarehouseId != null
                && !requestedWarehouseId.equals(order.getFulfillmentWarehouseId())) {
            if (order.getStatus() == OrderStatus.RESERVED) {
                throw new ConflictException(
                        "A reserved order must be released before changing warehouse");
            }
            warehouses.lockActiveWarehouse(requestedWarehouseId);
            order.changeFulfillmentWarehouse(requestedWarehouseId);
        }
        if (order.getStatus() == OrderStatus.RESERVED) {
            releaseReservations(order, requireActor(responsibleUser));
            order.release();
        }
        order.replaceItems(priceItems(validateItems(request.items())));
        orders.flush();
        return OrderResponse.from(order);
    }

    @Transactional
    public void deletePending(UUID id, String responsibleUser) {
        SalesOrder order = requireForUpdate(id);
        if (order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.RESERVED) {
            throw new ConflictException(
                    "Only a pending or reserved order can be deleted");
        }
        if (order.getStatus() == OrderStatus.RESERVED) {
            releaseReservations(order, requireActor(responsibleUser));
        }
        orders.delete(order);
    }
    @Transactional
    public OrderResponse reserve(UUID id, String responsibleUser) {
        SalesOrder order = requireForUpdate(id);
        if (order.getStatus() == OrderStatus.RESERVED) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Only a pending order can be reserved");
        }
        String actor = requireActor(responsibleUser);
        sortedItems(order).forEach(item -> inventory.reserveForOrder(
                order.getFulfillmentWarehouseId(), item.getProductId(), item.getQuantity(), order.getId(), actor));
        order.reserve(actor);
        orders.flush();
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse release(UUID id, String responsibleUser) {
        SalesOrder order = requireForUpdate(id);
        if (order.getStatus() == OrderStatus.PENDING) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new ConflictException("Only a reserved order can be released");
        }
        releaseReservations(order, requireActor(responsibleUser));
        order.release();
        orders.flush();
        return OrderResponse.from(order);
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
        if (order.getStatus() == OrderStatus.PENDING) {
            throw new ConflictException(
                    "A pending order must be reserved before confirmation");
        }
        String actor = requireActor(responsibleUser);
        sortedItems(order).forEach(item -> inventory.consumeReservation(
                order.getFulfillmentWarehouseId(), item.getProductId(), item.getQuantity(), order.getId(), actor));
        order.confirm(actor);
        orders.flush();
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UUID id, String responsibleUser) {
        SalesOrder order = requireForUpdate(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() == OrderStatus.PENDING
                || order.getStatus() == OrderStatus.RESERVED) {
            throw new ConflictException(
                    "Only a confirmed order can be cancelled");
        }
        String actor = requireActor(responsibleUser);
        sortedItems(order).forEach(item -> inventory.restoreForOrder(
                order.getFulfillmentWarehouseId(), item.getProductId(), item.getQuantity(), order.getId(), actor));
        order.cancel(actor);
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

    private List<CreateOrderItemRequest> validateItems(List<CreateOrderItemRequest> items) {
        return validateItems(new CreateOrderRequest(items));
    }

    private List<PricedOrderItem> priceItems(List<CreateOrderItemRequest> items) {
        return items.stream()
                .sorted(Comparator.comparing(CreateOrderItemRequest::productId))
                .map(item -> {
                    ProductSnapshot product = products.requireProductSnapshot(item.productId());
                    return new PricedOrderItem(
                            product.id(), item.quantity(), product.price());
                })
                .toList();
    }

    private Specification<SalesOrder> filters(OrderStatus status, Instant from, Instant to,
                                               UUID customerId, String folio) {
        String normalizedFolio = folio == null || folio.isBlank()
                ? null : folio.trim().toLowerCase(java.util.Locale.ROOT);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (customerId != null) {
                predicates.add(builder.equal(root.get("customerId"), customerId));
            }
            if (normalizedFolio != null) {
                predicates.add(builder.like(builder.lower(root.get("folio")),
                        "%" + normalizedFolio + "%"));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SalesOrder requireForUpdate(UUID id) {
        return orders.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private List<OrderItem> sortedItems(SalesOrder order) {
        return order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .toList();
    }

    private void releaseReservations(SalesOrder order, String actor) {
        sortedItems(order).forEach(item -> inventory.releaseForOrder(
                order.getFulfillmentWarehouseId(), item.getProductId(), order.getId(), actor));
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
