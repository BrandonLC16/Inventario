package com.example.inventory.warehouses;

import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.shared.PageResponse;
import com.example.inventory.shared.PageSupport;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WarehouseService implements WarehouseDirectory {
    private final WarehouseRepository repository;

    WarehouseService(WarehouseRepository repository) { this.repository = repository; }

    PageResponse<WarehouseResponse> findAll(int page, int size) {
        var pageable = PageSupport.request(page, size,
                Sort.by("code").ascending().and(Sort.by("id").ascending()));
        return PageResponse.from(repository.findAll(pageable), WarehouseResponse::from);
    }

    WarehouseResponse findById(UUID id) { return WarehouseResponse.from(findEntity(id)); }

    @Transactional
    WarehouseResponse create(WarehouseRequest request) {
        String code = normalizeCode(request.code());
        ensureCodeAvailable(code, null);
        Warehouse warehouse = repository.saveAndFlush(new Warehouse(
                code, request.name().trim(), trimToNull(request.description()), request.active()));
        repository.initializeProductSettings(warehouse.getId());
        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    WarehouseResponse update(UUID id, WarehouseRequest request) {
        Warehouse warehouse = findForUpdate(id);
        String code = normalizeCode(request.code());
        ensureCodeAvailable(code, id);
        if (warehouse.isActive() && !request.active()) ensureCanDeactivate(id);
        warehouse.update(code, request.name().trim(), trimToNull(request.description()), request.active());
        repository.flush();
        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    void deactivate(UUID id) {
        Warehouse warehouse = findForUpdate(id);
        if (!warehouse.isActive()) return;
        ensureCanDeactivate(id);
        warehouse.update(warehouse.getCode(), warehouse.getName(), warehouse.getDescription(), false);
        repository.flush();
    }

    @Override public void requireWarehouse(UUID warehouseId) { findEntity(warehouseId); }
    @Override public void lockWarehouse(UUID warehouseId) { findForUpdate(warehouseId); }

    @Override
    public void lockActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = findForUpdate(warehouseId);
        if (!warehouse.isActive()) {
            throw new ConflictException("Warehouse %s is inactive".formatted(warehouseId));
        }
    }

    @Override
    public void requireActiveProduct(UUID warehouseId, UUID productId) {
        if (!repository.isProductActive(warehouseId, productId)) {
            throw new ConflictException("Product %s is inactive in warehouse %s"
                    .formatted(productId, warehouseId));
        }
    }

    @Override
    @Transactional
    public void registerProduct(UUID productId) {
        repository.registerProduct(productId);
    }

    @Override
    @Transactional
    public void configureProduct(UUID warehouseId, UUID productId, int minimumStock, boolean active) {
        if (minimumStock < 0) throw new BadRequestException("Minimum stock cannot be negative");
        lockWarehouse(warehouseId);
        repository.configureProduct(warehouseId, productId, minimumStock, active);
    }

    private void ensureCanDeactivate(UUID id) {
        if (repository.hasStock(id)) throw new ConflictException("A warehouse with stock cannot be deactivated");
        if (repository.hasReservations(id)) throw new ConflictException("A warehouse with reservations cannot be deactivated");
        if (repository.hasOpenOrders(id)) throw new ConflictException("A warehouse with open orders cannot be deactivated");
    }

    private Warehouse findEntity(UUID id) {
        if (id == null) throw new BadRequestException("Warehouse is required");
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private Warehouse findForUpdate(UUID id) {
        if (id == null) throw new BadRequestException("Warehouse is required");
        return repository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private void ensureCodeAvailable(String code, UUID currentId) {
        boolean exists = currentId == null ? repository.existsByCodeIgnoreCase(code)
                : repository.existsByCodeIgnoreCaseAndIdNot(code, currentId);
        if (exists) throw new ConflictException("Warehouse code %s already exists".formatted(code));
    }

    private String normalizeCode(String code) { return code.trim().toUpperCase(Locale.ROOT); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private NotFoundException notFound(UUID id) { return new NotFoundException("Warehouse %s was not found".formatted(id)); }
}
