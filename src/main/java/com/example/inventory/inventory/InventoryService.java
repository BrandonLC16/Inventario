package com.example.inventory.inventory;

import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import com.example.inventory.warehouses.WarehouseDirectory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
class InventoryService implements InventoryOperations {
    private final InventoryRepository repository;
    private final InventoryReservationRepository reservations;
    private final StockMovementRepository movementRepository;
    private final ProductCatalog productCatalog;
    private final WarehouseDirectory warehouses;

    InventoryService(InventoryRepository repository,
                     InventoryReservationRepository reservations,
                     StockMovementRepository movementRepository,
                     ProductCatalog productCatalog,
                     WarehouseDirectory warehouses) {
        this.repository = repository;
        this.reservations = reservations;
        this.movementRepository = movementRepository;
        this.productCatalog = productCatalog;
        this.warehouses = warehouses;
    }

    InventoryResponse findByProductId(UUID warehouseId, UUID productId) {
        warehouses.requireWarehouse(warehouseId);
        productCatalog.requireVisibleProduct(productId);
        return repository.findBalance(warehouseId, productId)
                .map(InventoryResponse::from)
                .orElseGet(() -> InventoryResponse.empty(warehouseId, productId));
    }

    PageResponse<InventoryResponse> findAll(UUID warehouseId, int page, int size) {
        warehouses.requireWarehouse(warehouseId);
        var pageable = PageSupport.request(page, size, Sort.unsorted());
        return PageResponse.from(repository.findBalances(warehouseId, pageable),
                InventoryResponse::from);
    }

    InventorySettingResponse findSetting(UUID warehouseId, UUID productId) {
        warehouses.requireWarehouse(warehouseId);
        productCatalog.requireVisibleProduct(productId);
        return repository.findSetting(warehouseId, productId)
                .map(InventorySettingResponse::from)
                .orElseThrow(() -> new NotFoundException(
                        "Inventory setting for warehouse %s and product %s was not found"
                                .formatted(warehouseId, productId)));
    }

    PageResponse<InventorySettingResponse> findSettings(UUID warehouseId,
                                                        int page, int size) {
        warehouses.requireWarehouse(warehouseId);
        var pageable = PageSupport.request(page, size, Sort.unsorted());
        return PageResponse.from(repository.findSettings(warehouseId, pageable),
                InventorySettingResponse::from);
    }

    @Transactional
    InventoryResponse adjust(UUID warehouseId, UUID productId, int delta,
                             String reference, String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        if (delta == 0) throw new BadRequestException("Inventory adjustment must not be zero");
        lockActiveWarehouseProduct(warehouseId, productId);
        boolean initialStock = repository.ensureExists(warehouseId, productId) == 1;
        InventoryItem item = lockInventory(warehouseId, productId);
        int balanceBefore = item.getQuantity();
        int reserved = reservedQuantity(warehouseId, productId);
        if (delta < 0 && -((long) delta) > item.getQuantity() - (long) reserved) {
            throw insufficientStock(productId);
        }
        try {
            item.changeQuantity(delta);
        } catch (IllegalArgumentException exception) {
            if (delta < 0) throw insufficientStock(productId);
            throw new BadRequestException(exception.getMessage());
        }
        StockMovementType type = initialStock && delta > 0
                ? StockMovementType.INITIAL_STOCK
                : delta > 0 ? StockMovementType.MANUAL_IN : StockMovementType.MANUAL_OUT;
        String businessReference = normalizeReference(reference);
        recordMovement(item, type, delta, balanceBefore, 0, reserved, reserved,
                businessReference == null ? "MANUAL:" + UUID.randomUUID() : businessReference,
                actor);
        return InventoryResponse.from(item, reserved);
    }

    PageResponse<LowStockResponse> findLowStock(UUID warehouseId, int page, int size,
                                                String search, boolean outOfStockOnly) {
        warehouses.requireWarehouse(warehouseId);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        var pageable = PageSupport.request(page, size, Sort.unsorted());
        return PageResponse.from(repository.findLowStock(
                warehouseId, normalizedSearch, outOfStockOnly, pageable),
                LowStockResponse::from);
    }

    @Transactional
    void configureProduct(UUID warehouseId, UUID productId, int minimumStock, boolean active) {
        productCatalog.requireProduct(productId);
        warehouses.configureProduct(warehouseId, productId, minimumStock, active);
    }

    @Override
    @Transactional
    public void reserveForOrder(UUID warehouseId, UUID productId, int quantity,
                                UUID orderId, String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        requirePositive(quantity);
        lockActiveWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        InventoryItem item = lockInventory(warehouseId, productId);
        int reservedBefore = reservedQuantity(warehouseId, productId);
        if (quantity > item.getQuantity() - reservedBefore) throw insufficientStock(productId);
        if (reservations.findForUpdate(orderId, warehouseId, productId).isPresent()) {
            throw new ConflictException("Inventory is already reserved for this order and product");
        }
        reservations.save(new InventoryReservation(
                orderId, warehouseId, productId, quantity, actor));
        recordMovement(item, StockMovementType.ORDER_RESERVED, 0, item.getQuantity(),
                quantity, reservedBefore, Math.addExact(reservedBefore, quantity),
                orderId.toString(), actor);
    }

    @Override
    @Transactional
    public void releaseForOrder(UUID warehouseId, UUID productId, UUID orderId,
                                String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        lockStoredWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        InventoryItem item = lockInventory(warehouseId, productId);
        InventoryReservation reservation = reservations
                .findForUpdate(orderId, warehouseId, productId)
                .orElseThrow(() -> new ConflictException("Inventory reservation was not found"));
        int reservedBefore = reservedQuantity(warehouseId, productId);
        reservations.delete(reservation);
        recordMovement(item, StockMovementType.ORDER_RESERVATION_RELEASED, 0,
                item.getQuantity(), -reservation.getQuantity(), reservedBefore,
                reservedBefore - reservation.getQuantity(), orderId.toString(), actor);
    }

    @Override
    @Transactional
    public void consumeReservation(UUID warehouseId, UUID productId, int quantity,
                                   UUID orderId, String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        requirePositive(quantity);
        lockStoredWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        InventoryItem item = lockInventory(warehouseId, productId);
        InventoryReservation reservation = reservations
                .findForUpdate(orderId, warehouseId, productId)
                .orElseThrow(() -> new ConflictException("Inventory reservation was not found"));
        if (reservation.getQuantity() != quantity) {
            throw new ConflictException("Inventory reservation does not match the order item");
        }
        int balanceBefore = item.getQuantity();
        int reservedBefore = reservedQuantity(warehouseId, productId);
        try {
            item.changeQuantity(-quantity);
        } catch (IllegalArgumentException exception) {
            throw insufficientStock(productId);
        }
        reservations.delete(reservation);
        recordMovement(item, StockMovementType.ORDER_CONFIRMED, -quantity,
                balanceBefore, -quantity, reservedBefore,
                reservedBefore - quantity, orderId.toString(), actor);
    }

    @Override
    @Transactional
    public void restoreForOrder(UUID warehouseId, UUID productId, int quantity,
                                UUID orderId, String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        requirePositive(quantity);
        lockStoredWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        InventoryItem item = lockInventory(warehouseId, productId);
        int balanceBefore = item.getQuantity();
        int reserved = reservedQuantity(warehouseId, productId);
        try {
            item.changeQuantity(quantity);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException(exception.getMessage());
        }
        recordMovement(item, StockMovementType.ORDER_CANCELLED, quantity,
                balanceBefore, 0, reserved, reserved, orderId.toString(), actor);
    }

    private void lockActiveWarehouseProduct(UUID warehouseId, UUID productId) {
        warehouses.lockActiveWarehouse(warehouseId);
        productCatalog.requireProduct(productId);
        warehouses.requireActiveProduct(warehouseId, productId);
    }

    private void lockStoredWarehouseProduct(UUID warehouseId, UUID productId) {
        warehouses.lockWarehouse(warehouseId);
        productCatalog.lockStoredProduct(productId);
    }

    private InventoryItem lockInventory(UUID warehouseId, UUID productId) {
        return repository.findForUpdate(warehouseId, productId)
                .orElseThrow(() -> new ConflictException(
                        "Inventory for warehouse %s and product %s could not be initialized"
                                .formatted(warehouseId, productId)));
    }

    private BadRequestException insufficientStock(UUID productId) {
        return new BadRequestException("Inventory quantity cannot be negative");
    }

    private int reservedQuantity(UUID warehouseId, UUID productId) {
        try {
            return Math.toIntExact(reservations.reservedQuantity(warehouseId, productId));
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Reserved inventory is outside the supported range", exception);
        }
    }

    private void recordMovement(InventoryItem item, StockMovementType type,
                                int quantityDelta, int balanceBefore,
                                int reservationDelta, int reservedBefore,
                                int reservedAfter, String businessReference,
                                String responsibleUser) {
        movementRepository.save(new StockMovement(item.getWarehouseId(), item.getProductId(),
                type, quantityDelta, balanceBefore, item.getQuantity(),
                reservationDelta, reservedBefore, reservedAfter,
                businessReference, responsibleUser));
    }

    @Override
    @Transactional
    public void receivePurchase(UUID warehouseId, UUID productId, int quantity,
                                UUID receiptId, String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        requirePositive(quantity);
        if (receiptId == null) {
            throw new IllegalArgumentException("The purchase receipt is required");
        }
        lockActiveWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        InventoryItem item = lockInventory(warehouseId, productId);
        int balanceBefore = item.getQuantity();
        int reserved = reservedQuantity(warehouseId, productId);
        try {
            item.changeQuantity(quantity);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException(exception.getMessage());
        }
        recordMovement(item, StockMovementType.PURCHASE_RECEIVED, quantity,
                balanceBefore, 0, reserved, reserved, receiptId.toString(), actor);
    }

    @Override
    @Transactional
    public void transferOut(UUID warehouseId, UUID productId, int quantity,
                            UUID transferId, String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        requirePositive(quantity);
        requireTransfer(transferId);
        lockActiveWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        InventoryItem item = lockInventory(warehouseId, productId);
        int balanceBefore = item.getQuantity();
        int reserved = reservedQuantity(warehouseId, productId);
        if (quantity > item.getQuantity() - (long) reserved) {
            throw new BadRequestException(
                    "Insufficient available inventory for transfer");
        }
        try {
            item.changeQuantity(-quantity);
        } catch (IllegalArgumentException exception) {
            throw insufficientStock(productId);
        }
        recordMovement(item, StockMovementType.TRANSFER_OUT, -quantity,
                balanceBefore, 0, reserved, reserved, transferId.toString(), actor);
    }

    @Override
    @Transactional
    public void transferIn(UUID warehouseId, UUID productId, int quantity,
                           UUID transferId, String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        requirePositive(quantity);
        requireTransfer(transferId);
        lockActiveWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        InventoryItem item = lockInventory(warehouseId, productId);
        int balanceBefore = item.getQuantity();
        int reserved = reservedQuantity(warehouseId, productId);
        try {
            item.changeQuantity(quantity);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException(exception.getMessage());
        }
        recordMovement(item, StockMovementType.TRANSFER_IN, quantity,
                balanceBefore, 0, reserved, reserved, transferId.toString(), actor);
    }

    @Override
    @Transactional
    public int lockAndGetQuantityForPhysicalCount(
            UUID warehouseId, UUID productId) {
        lockActiveWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        return lockInventory(warehouseId, productId).getQuantity();
    }

    @Override
    @Transactional
    public PhysicalCountSnapshot capturePhysicalCountExpectation(
            UUID warehouseId, UUID productId, int previousExpectedQuantity,
            java.time.Instant previousSnapshotAt) {
        if (previousExpectedQuantity < 0 || previousSnapshotAt == null) {
            throw new IllegalArgumentException(
                    "The previous physical count snapshot is required");
        }
        lockActiveWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        lockInventory(warehouseId, productId);
        java.time.Instant capturedAt = java.time.Instant.now();
        long movementDelta = movementRepository.quantityDeltaBetween(
                warehouseId, productId, previousSnapshotAt, capturedAt);
        long expected = previousExpectedQuantity + movementDelta;
        if (expected < 0 || expected > Integer.MAX_VALUE) {
            throw new ConflictException(
                    "Expected physical count quantity is outside the supported range");
        }
        return new PhysicalCountSnapshot((int) expected, capturedAt);
    }

    @Override
    @Transactional
    public void postPhysicalCountAdjustment(
            UUID warehouseId, UUID productId, int variance,
            UUID countId, String responsibleUser) {
        String actor = requireResponsibleUser(responsibleUser);
        if (countId == null) {
            throw new IllegalArgumentException("The physical inventory count is required");
        }
        lockActiveWarehouseProduct(warehouseId, productId);
        repository.ensureExists(warehouseId, productId);
        InventoryItem item = lockInventory(warehouseId, productId);
        int balanceBefore = item.getQuantity();
        int reserved = reservedQuantity(warehouseId, productId);
        long adjustedQuantity = balanceBefore + (long) variance;
        if (adjustedQuantity < reserved) {
            throw new ConflictException(
                    "Physical count result cannot be below reserved inventory");
        }
        if (adjustedQuantity > Integer.MAX_VALUE) {
            throw new ConflictException(
                    "Inventory quantity is outside the supported range");
        }
        if (variance == 0) return;
        try {
            item.changeQuantity(variance);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException(exception.getMessage());
        }
        recordMovement(item, StockMovementType.PHYSICAL_COUNT_ADJUSTMENT,
                variance, balanceBefore, 0, reserved, reserved,
                countId.toString(), actor);
    }

    private void requireTransfer(UUID transferId) {
        if (transferId == null) {
            throw new IllegalArgumentException("The inventory transfer is required");
        }
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) throw new BadRequestException("Quantity must be greater than zero");
    }

    private String requireResponsibleUser(String responsibleUser) {
        if (responsibleUser == null || responsibleUser.isBlank()) {
            throw new IllegalArgumentException("The responsible user is required");
        }
        return responsibleUser.trim();
    }

    private String normalizeReference(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String normalized = reference.trim();
        if (normalized.length() > 128) {
            throw new BadRequestException("Reference must not exceed 128 characters");
        }
        return normalized;
    }
}
