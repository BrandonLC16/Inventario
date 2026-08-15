package com.example.inventory.purchases;

import com.example.inventory.inventory.InventoryOperations;
import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import com.example.inventory.suppliers.SupplierDirectory;
import com.example.inventory.warehouses.WarehouseDirectory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrders;
    private final PurchaseOrderItemRepository purchaseOrderItems;
    private final PurchaseReceiptRepository receipts;
    private final PurchaseReceiptItemRepository receiptItems;
    private final ProductCatalog products;
    private final SupplierDirectory suppliers;
    private final WarehouseDirectory warehouses;
    private final InventoryOperations inventory;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrders,
            PurchaseOrderItemRepository purchaseOrderItems,
            PurchaseReceiptRepository receipts,
            PurchaseReceiptItemRepository receiptItems,
            ProductCatalog products,
            SupplierDirectory suppliers,
            WarehouseDirectory warehouses,
            InventoryOperations inventory) {
        this.purchaseOrders = purchaseOrders;
        this.purchaseOrderItems = purchaseOrderItems;
        this.receipts = receipts;
        this.receiptItems = receiptItems;
        this.products = products;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.inventory = inventory;
    }

    public PageResponse<PurchaseOrderResponse> findAll(
            int page, int size, PurchaseOrderStatus status,
            Instant from, Instant to, UUID supplierId,
            UUID destinationWarehouseId, String folio) {
        PageSupport.validateDateRange(from, to);
        var pageable = PageSupport.request(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(purchaseOrders.findAll(filters(
                        status, from, to, supplierId, destinationWarehouseId, folio),
                pageable), PurchaseOrderResponse::from);
    }

    public PurchaseOrderResponse findById(UUID id) {
        return PurchaseOrderResponse.from(purchaseOrders.findDetailedById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request,
                                        String responsibleUser) {
        if (request == null) {
            throw new BadRequestException("A purchase order request is required");
        }
        List<PurchaseOrderLine> lines = validateAndMapItems(request.items());
        suppliers.requireActiveSupplier(request.supplierId());
        warehouses.lockActiveWarehouse(request.destinationWarehouseId());
        String currency = normalizeCurrency(request.currency());
        String folio = "PO-%010d".formatted(purchaseOrders.nextFolioSequence());
        PurchaseOrder purchaseOrder = new PurchaseOrder(
                folio, request.supplierId(), request.destinationWarehouseId(),
                currency, trimToNull(request.supplierReference()),
                requireActor(responsibleUser), lines);
        return PurchaseOrderResponse.from(
                purchaseOrders.saveAndFlush(purchaseOrder));
    }

    @Transactional
    public PurchaseOrderResponse replaceItems(
            UUID id, UpdatePurchaseOrderItemsRequest request) {
        PurchaseOrder purchaseOrder = requireForUpdate(id);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException(
                    "Only a draft purchase order can change items or destination");
        }
        if (request == null) {
            throw new BadRequestException("Purchase order items are required");
        }
        List<PurchaseOrderLine> lines = validateAndMapItems(request.items());
        UUID requestedWarehouse = request.destinationWarehouseId();
        if (requestedWarehouse != null
                && !requestedWarehouse.equals(
                purchaseOrder.getDestinationWarehouseId())) {
            warehouses.lockActiveWarehouse(requestedWarehouse);
            purchaseOrder.changeDestinationWarehouse(requestedWarehouse);
        }
        purchaseOrder.replaceItems(lines);
        purchaseOrders.flush();
        return PurchaseOrderResponse.from(purchaseOrder);
    }

    @Transactional
    public PurchaseOrderResponse issue(UUID id, String responsibleUser) {
        PurchaseOrder purchaseOrder = requireForUpdate(id);
        if (purchaseOrder.getStatus() == PurchaseOrderStatus.ISSUED
                || purchaseOrder.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED
                || purchaseOrder.getStatus() == PurchaseOrderStatus.RECEIVED) {
            return PurchaseOrderResponse.from(purchaseOrder);
        }
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException(
                    "Only a draft purchase order can be issued");
        }
        if (purchaseOrder.getItems().isEmpty()) {
            throw new ConflictException(
                    "A purchase order must contain items before it is issued");
        }
        purchaseOrder.issue(requireActor(responsibleUser));
        purchaseOrders.flush();
        return PurchaseOrderResponse.from(purchaseOrder);
    }

    @Transactional
    public PurchaseReceiptResult receive(
            UUID id, CreatePurchaseReceiptRequest request,
            String responsibleUser) {
        if (request == null) {
            throw new BadRequestException("A purchase receipt request is required");
        }
        String externalReference = normalizeExternalReference(
                request.externalReference());
        List<CreatePurchaseReceiptItemRequest> requestedItems =
                validateReceiptItems(request.items());
        String actor = requireActor(responsibleUser);

        PurchaseOrder purchaseOrder = requireForUpdate(id);
        List<PurchaseOrderItem> lockedItems = purchaseOrderItems
                .findByPurchaseOrderIdForUpdate(id);

        var existing = receipts.findByPurchaseOrderIdAndExternalReference(
                id, externalReference);
        if (existing.isPresent()) {
            PurchaseReceipt receipt = existing.get();
            List<PurchaseReceiptItem> existingItems =
                    receiptItems.findByReceiptId(receipt.getId());
            ensureIdempotentRetry(receipt, existingItems, requestedItems,
                    request.updateSupplierProductLastCost(), externalReference);
            return new PurchaseReceiptResult(
                    PurchaseReceiptResponse.from(receipt, existingItems), false);
        }

        if (purchaseOrder.getStatus() != PurchaseOrderStatus.ISSUED
                && purchaseOrder.getStatus()
                != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new ConflictException(
                    "Only an issued purchase order can receive products");
        }

        Map<UUID, PurchaseOrderItem> itemsById = lockedItems.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId,
                        Function.identity()));
        List<ReceiptLine> lines = requestedItems.stream()
                .map(requested -> validateReceiptLine(requested, itemsById))
                .sorted(Comparator.comparing(
                        line -> line.orderItem().getProductId()))
                .toList();

        String folio = "PR-%010d".formatted(receipts.nextFolioSequence());
        PurchaseReceipt receipt = new PurchaseReceipt(
                folio, id, purchaseOrder.getDestinationWarehouseId(),
                externalReference,
                request.updateSupplierProductLastCost(), actor);
        receipts.saveAndFlush(receipt);

        List<PurchaseReceiptItem> persistedItems = lines.stream()
                .map(line -> new PurchaseReceiptItem(
                        receipt.getId(), line.orderItem().getId(),
                        line.request().quantity(), line.request().unitCost()))
                .toList();
        receiptItems.saveAllAndFlush(persistedItems);

        lines.forEach(line -> inventory.receivePurchase(
                purchaseOrder.getDestinationWarehouseId(),
                line.orderItem().getProductId(), line.request().quantity(),
                receipt.getId(), actor));
        lines.forEach(line -> line.orderItem().receive(line.request().quantity()));
        purchaseOrder.recordReceipt(lockedItems.stream()
                .allMatch(item -> item.pendingQuantity() == 0));

        if (request.updateSupplierProductLastCost()) {
            lines.forEach(line -> suppliers.updateLastUnitCostIfAssociated(
                    purchaseOrder.getSupplierId(), line.orderItem().getProductId(),
                    line.request().unitCost()));
        }
        purchaseOrders.flush();
        return new PurchaseReceiptResult(
                PurchaseReceiptResponse.from(receipt, persistedItems), true);
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID id, String responsibleUser) {
        PurchaseOrder purchaseOrder = requireForUpdate(id);
        if (purchaseOrder.getStatus() == PurchaseOrderStatus.CANCELLED) {
            return PurchaseOrderResponse.from(purchaseOrder);
        }
        if (receipts.existsByPurchaseOrderId(id)) {
            throw new ConflictException(
                    "A purchase order with receipts cannot be cancelled");
        }
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT
                && purchaseOrder.getStatus() != PurchaseOrderStatus.ISSUED) {
            throw new ConflictException(
                    "This purchase order cannot be cancelled");
        }
        purchaseOrder.cancel(requireActor(responsibleUser));
        purchaseOrders.flush();
        return PurchaseOrderResponse.from(purchaseOrder);
    }

    public List<PurchaseReceiptResponse> findReceipts(UUID purchaseOrderId) {
        if (!purchaseOrders.existsById(purchaseOrderId)) {
            throw notFound(purchaseOrderId);
        }
        List<PurchaseReceipt> orderReceipts = receipts
                .findByPurchaseOrderIdOrderByReceivedAtAscIdAsc(purchaseOrderId);
        if (orderReceipts.isEmpty()) return List.of();
        Map<UUID, List<PurchaseReceiptItem>> itemsByReceipt = receiptItems
                .findByReceiptIds(orderReceipts.stream()
                        .map(PurchaseReceipt::getId).toList())
                .stream().collect(Collectors.groupingBy(
                        PurchaseReceiptItem::getReceiptId));
        return orderReceipts.stream()
                .map(receipt -> PurchaseReceiptResponse.from(receipt,
                        itemsByReceipt.getOrDefault(receipt.getId(), List.of())))
                .toList();
    }

    private List<PurchaseOrderLine> validateAndMapItems(
            List<CreatePurchaseOrderItemRequest> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BadRequestException(
                    "A purchase order must contain at least one item");
        }
        if (requestedItems.size() > 100) {
            throw new BadRequestException(
                    "A purchase order cannot contain more than 100 items");
        }
        Set<UUID> productIds = new HashSet<>();
        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (CreatePurchaseOrderItemRequest item : requestedItems) {
            if (item == null || item.productId() == null
                    || item.orderedQuantity() <= 0
                    || item.orderedQuantity()
                    > CreatePurchaseOrderItemRequest.MAX_QUANTITY
                    || item.unitCost() == null
                    || item.unitCost().signum() < 0) {
                throw new BadRequestException(
                        "Every purchase order item requires a product, a quantity between 1 and "
                                + CreatePurchaseOrderItemRequest.MAX_QUANTITY
                                + " and a non-negative unit cost");
            }
            if (!productIds.add(item.productId())) {
                throw new BadRequestException(
                        "A purchase order must not contain duplicate products");
            }
            products.requireProduct(item.productId());
            lines.add(new PurchaseOrderLine(
                    item.productId(), trimToNull(item.supplierSku()),
                    item.orderedQuantity(), item.unitCost()));
        }
        return lines.stream().sorted(Comparator.comparing(
                PurchaseOrderLine::productId)).toList();
    }

    private List<CreatePurchaseReceiptItemRequest> validateReceiptItems(
            List<CreatePurchaseReceiptItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException(
                    "A purchase receipt must contain at least one item");
        }
        if (items.size() > 100) {
            throw new BadRequestException(
                    "A purchase receipt cannot contain more than 100 items");
        }
        Set<UUID> itemIds = new HashSet<>();
        for (CreatePurchaseReceiptItemRequest item : items) {
            if (item == null || item.purchaseOrderItemId() == null
                    || item.quantity() <= 0
                    || item.quantity()
                    > CreatePurchaseOrderItemRequest.MAX_QUANTITY
                    || item.unitCost() == null
                    || item.unitCost().signum() < 0) {
                throw new BadRequestException(
                        "Every receipt item requires an order item, a quantity between 1 and "
                                + CreatePurchaseOrderItemRequest.MAX_QUANTITY
                                + " and a non-negative unit cost");
            }
            if (!itemIds.add(item.purchaseOrderItemId())) {
                throw new BadRequestException(
                        "A purchase receipt must not contain duplicate order items");
            }
        }
        return List.copyOf(items);
    }

    private ReceiptLine validateReceiptLine(
            CreatePurchaseReceiptItemRequest request,
            Map<UUID, PurchaseOrderItem> itemsById) {
        PurchaseOrderItem item = itemsById.get(request.purchaseOrderItemId());
        if (item == null) {
            throw new BadRequestException(
                    "Purchase order item %s does not belong to this order"
                            .formatted(request.purchaseOrderItemId()));
        }
        if (request.quantity() > item.pendingQuantity()) {
            throw new BadRequestException(
                    "Received quantity for item %s exceeds its pending quantity"
                            .formatted(item.getId()));
        }
        return new ReceiptLine(item, request);
    }

    private void ensureIdempotentRetry(
            PurchaseReceipt existingReceipt,
            List<PurchaseReceiptItem> existingItems,
            List<CreatePurchaseReceiptItemRequest> requestedItems,
            boolean updateSupplierProductLastCost,
            String externalReference) {
        Map<UUID, PurchaseReceiptItem> existingByOrderItem = existingItems.stream()
                .collect(Collectors.toMap(
                        PurchaseReceiptItem::getPurchaseOrderItemId,
                        Function.identity()));
        boolean matches = Boolean.valueOf(updateSupplierProductLastCost)
                .equals(existingReceipt.getUpdateSupplierProductLastCost())
                && existingByOrderItem.size() == requestedItems.size()
                && requestedItems.stream().allMatch(requested -> {
                    PurchaseReceiptItem existing = existingByOrderItem.get(
                            requested.purchaseOrderItemId());
                    return existing != null
                            && existing.getQuantity() == requested.quantity()
                            && existing.getUnitCost().compareTo(
                            requested.unitCost()) == 0;
                });
        if (!matches) {
            throw new ConflictException(
                    "External reference %s was already used with different receipt content"
                            .formatted(externalReference));
        }
    }

    private Specification<PurchaseOrder> filters(
            PurchaseOrderStatus status, Instant from, Instant to,
            UUID supplierId, UUID destinationWarehouseId, String folio) {
        String normalizedFolio = folio == null || folio.isBlank()
                ? null : folio.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("createdAt"), to));
            }
            if (supplierId != null) {
                predicates.add(builder.equal(root.get("supplierId"), supplierId));
            }
            if (destinationWarehouseId != null) {
                predicates.add(builder.equal(
                        root.get("destinationWarehouseId"),
                        destinationWarehouseId));
            }
            if (normalizedFolio != null) {
                predicates.add(builder.like(builder.lower(root.get("folio")),
                        "%" + normalizedFolio + "%"));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PurchaseOrder requireForUpdate(UUID id) {
        return purchaseOrders.findByIdForUpdate(id)
                .orElseThrow(() -> notFound(id));
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("Currency is required");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new BadRequestException(
                    "Currency must be a three-letter ISO code");
        }
        return normalized;
    }

    private String normalizeExternalReference(String reference) {
        String normalized = trimToNull(reference);
        if (normalized == null) {
            throw new BadRequestException("External reference is required");
        }
        if (normalized.length() > 128) {
            throw new BadRequestException(
                    "External reference must not exceed 128 characters");
        }
        return normalized;
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("The responsible user is required");
        }
        return actor.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException(
                "Purchase order %s was not found".formatted(id));
    }

    private record ReceiptLine(
            PurchaseOrderItem orderItem,
            CreatePurchaseReceiptItemRequest request) {
    }
}
