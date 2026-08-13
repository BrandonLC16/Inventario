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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository repository;

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

    private WarehouseService service() {
        return new WarehouseService(repository);
    }

    private Warehouse warehouse() {
        return new Warehouse("TEST", "Test", null, true);
    }
}
