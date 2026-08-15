package com.example.inventory.warehouses;

import com.example.inventory.shared.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository repository;

    @Test
    void createLocksCatalogRegistrationBeforeCheckingAndInserting() {
        Warehouse main = warehouse();
        when(repository.findByIdForUpdate(WarehouseDirectory.MAIN_WAREHOUSE_ID))
                .thenReturn(Optional.of(main));
        when(repository.saveAndFlush(any(Warehouse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().create(new WarehouseRequest(" NEW ", " New warehouse ", null, true));

        var order = inOrder(repository);
        order.verify(repository).findByIdForUpdate(WarehouseDirectory.MAIN_WAREHOUSE_ID);
        order.verify(repository).existsByCodeIgnoreCase("NEW");
        order.verify(repository).saveAndFlush(any(Warehouse.class));
    }

    @Test
    void productWithStockCannotBeDeactivated() {
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(repository.findByIdForUpdate(warehouseId))
                .thenReturn(Optional.of(warehouse()));
        when(repository.hasProductStock(warehouseId, productId)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service().configureProduct(warehouseId, productId, 3, false));

        assertEquals("A warehouse product with stock cannot be deactivated",
                error.getMessage());
        verify(repository, never()).configureProduct(warehouseId, productId, 3, false);
    }

    @Test
    void productWithReservationsCannotBeDeactivated() {
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(repository.findByIdForUpdate(warehouseId))
                .thenReturn(Optional.of(warehouse()));
        when(repository.hasProductReservations(warehouseId, productId)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service().configureProduct(warehouseId, productId, 3, false));

        assertEquals("A warehouse product with reservations cannot be deactivated",
                error.getMessage());
        verify(repository, never()).configureProduct(warehouseId, productId, 3, false);
    }

    @Test
    void productWithPhysicalInventoryCannotBeDeleted() {
        UUID productId = UUID.randomUUID();
        when(repository.hasStockForProduct(productId)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service().ensureProductCanBeDeleted(productId));

        assertEquals("A product with physical inventory cannot be deleted",
                error.getMessage());
        verify(repository, never()).hasReservationsForProduct(productId);
    }

    @Test
    void productWithReservationsCannotBeDeleted() {
        UUID productId = UUID.randomUUID();
        when(repository.hasReservationsForProduct(productId)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service().ensureProductCanBeDeleted(productId));

        assertEquals("A product with inventory reservations cannot be deleted",
                error.getMessage());
        verify(repository, never()).hasPendingOperationsForProduct(productId);
    }

    @Test
    void productUsedByPendingOperationsCannotBeDeleted() {
        UUID productId = UUID.randomUUID();
        when(repository.hasPendingOperationsForProduct(productId)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service().ensureProductCanBeDeleted(productId));

        assertEquals("A product used by pending operations cannot be deleted",
                error.getMessage());
    }

    private WarehouseService service() {
        return new WarehouseService(repository);
    }

    private Warehouse warehouse() {
        return new Warehouse("TEST", "Test", null, true);
    }
}
