package com.example.inventory.inventory;

import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import com.example.inventory.warehouses.WarehouseDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final UUID WAREHOUSE_ID = WarehouseDirectory.MAIN_WAREHOUSE_ID;

    @Mock
    private InventoryRepository repository;

    @Mock
    private InventoryReservationRepository reservations;

    @Mock
    private StockMovementRepository movementRepository;

    @Mock
    private ProductCatalog productCatalog;

    @Mock
    private WarehouseDirectory warehouses;

    @Test
    void findByProductIdReturnsExistingInventory() {
        UUID productId = UUID.randomUUID();
        when(repository.findBalance(WAREHOUSE_ID, productId))
                .thenReturn(Optional.of(balance(productId, 7, 2)));

        InventoryResponse response = service().findByProductId(WAREHOUSE_ID, productId);

        assertEquals(productId, response.productId());
        assertEquals(7, response.quantity());
        assertEquals(2, response.reservedQuantity());
        assertEquals(5, response.availableQuantity());
        verify(productCatalog).requireProduct(productId);
    }

    @Test
    void findByProductIdReturnsZeroWhenInventoryDoesNotExist() {
        UUID productId = UUID.randomUUID();
        when(repository.findBalance(WAREHOUSE_ID, productId)).thenReturn(Optional.empty());

        InventoryResponse response = service().findByProductId(WAREHOUSE_ID, productId);

        assertEquals(productId, response.productId());
        assertEquals(0, response.quantity());
    }

    @Test
    void findByProductIdStopsWhenProductDoesNotExist() {
        UUID productId = UUID.randomUUID();
        doThrow(new NotFoundException("missing")).when(productCatalog).requireProduct(productId);

        assertThrows(NotFoundException.class, () -> service().findByProductId(WAREHOUSE_ID, productId));

        verifyNoInteractions(repository, movementRepository);
    }

    @Test
    void firstPositiveAdjustmentRecordsInitialStock() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 0);
        when(repository.ensureExists(WAREHOUSE_ID, productId)).thenReturn(1);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));

        InventoryResponse response = service().adjust(WAREHOUSE_ID, productId, 8, null, "operator");

        assertEquals(8, response.quantity());
        assertMovement(StockMovementType.INITIAL_STOCK, 8, 0, 8, null, "operator");
    }

    @Test
    void laterPositiveAdjustmentRecordsManualInput() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 4);
        when(repository.ensureExists(WAREHOUSE_ID, productId)).thenReturn(0);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));

        InventoryResponse response = service().adjust(WAREHOUSE_ID, productId, 3, null, "operator");

        assertEquals(7, response.quantity());
        assertMovement(StockMovementType.MANUAL_IN, 3, 4, 7, null, "operator");
    }

    @Test
    void validNegativeAdjustmentRecordsManualOutput() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 9);
        when(repository.ensureExists(WAREHOUSE_ID, productId)).thenReturn(0);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));

        InventoryResponse response = service().adjust(WAREHOUSE_ID, productId, -5, null, "operator");

        assertEquals(4, response.quantity());
        assertMovement(StockMovementType.MANUAL_OUT, -5, 9, 4, null, "operator");
    }

    @Test
    void zeroAdjustmentIsRejectedWithoutRecordingMovement() {
        UUID productId = UUID.randomUUID();

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service().adjust(WAREHOUSE_ID, productId, 0, null, "operator"));

        assertEquals("Inventory adjustment must not be zero", error.getMessage());
        verify(repository, never()).ensureExists(any(), any());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void adjustmentRequiresResponsibleUserBeforeTouchingInventory() {
        assertThrows(IllegalArgumentException.class,
                () -> service().adjust(WAREHOUSE_ID, UUID.randomUUID(), 1, null, " "));

        verifyNoInteractions(productCatalog, repository, movementRepository);
    }

    @Test
    void insufficientStockIsRejectedWithoutChangingBalanceOrRecordingMovement() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 2);
        when(repository.ensureExists(WAREHOUSE_ID, productId)).thenReturn(0);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service().adjust(WAREHOUSE_ID, productId, -3, null, "operator"));

        assertEquals("Inventory quantity cannot be negative", error.getMessage());
        assertEquals(2, item.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void adjustmentOverflowIsRejectedWithoutChangingBalanceOrRecordingMovement() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, Integer.MAX_VALUE);
        when(repository.ensureExists(WAREHOUSE_ID, productId)).thenReturn(0);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service().adjust(WAREHOUSE_ID, productId, 1, null, "operator"));

        assertEquals("Inventory quantity is outside the supported range", error.getMessage());
        assertEquals(Integer.MAX_VALUE, item.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void adjustmentForMissingProductDoesNotTouchInventoryOrMovements() {
        UUID productId = UUID.randomUUID();
        doThrow(new NotFoundException("missing")).when(productCatalog).requireProduct(productId);

        assertThrows(NotFoundException.class, () -> service().adjust(WAREHOUSE_ID, productId, 1, null, "operator"));

        verifyNoInteractions(repository, movementRepository);
    }

    @Test
    void adjustmentFailsWithoutMovementWhenInventoryCannotBeLocked() {
        UUID productId = UUID.randomUUID();
        when(repository.ensureExists(WAREHOUSE_ID, productId)).thenReturn(1);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> service().adjust(WAREHOUSE_ID, productId, 1, null, "operator"));

        verifyNoInteractions(movementRepository);
    }

    @Test
    void adjustmentPropagatesInitializationPersistenceErrorWithoutMovement() {
        UUID productId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("database unavailable");
        when(repository.ensureExists(WAREHOUSE_ID, productId)).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class, () -> service().adjust(WAREHOUSE_ID, productId, 1, null, "operator"));

        assertSame(failure, thrown);
        verifyNoInteractions(movementRepository);
    }

    @Test
    void consumeReservationRecordsConfirmedOrderMovement() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 10);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));
        when(reservations.findForUpdate(orderId, WAREHOUSE_ID, productId))
                .thenReturn(Optional.of(new InventoryReservation(
                        orderId, WAREHOUSE_ID, productId, 4, "operator@example.com")));
        when(reservations.reservedQuantity(WAREHOUSE_ID, productId)).thenReturn(4L);

        service().consumeReservation(WAREHOUSE_ID, productId, 4, orderId, "operator@example.com");

        assertEquals(6, item.getQuantity());
        assertMovement(StockMovementType.ORDER_CONFIRMED, -4, 10, 6,
                orderId.toString(), "operator@example.com");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void consumeReservationRejectsNonPositiveQuantityWithoutInteractions(int quantity) {
        assertThrows(BadRequestException.class, () -> service().consumeReservation(
                WAREHOUSE_ID, UUID.randomUUID(), quantity, UUID.randomUUID(), "operator"));

        verifyNoInteractions(productCatalog, repository, movementRepository);
    }

    @Test
    void consumeReservationRejectsMissingProductWithoutMovement() {
        UUID productId = UUID.randomUUID();
        doThrow(new NotFoundException("missing"))
                .when(productCatalog).requireStoredProduct(productId);

        assertThrows(NotFoundException.class, () -> service().consumeReservation(
                WAREHOUSE_ID, productId, 1, UUID.randomUUID(), "operator"));

        verifyNoInteractions(repository, movementRepository);
    }

    @Test
    void consumeReservationRejectsInsufficientStockWithoutMovement() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 3);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));
        when(reservations.findForUpdate(orderId, WAREHOUSE_ID, productId))
                .thenReturn(Optional.of(new InventoryReservation(
                        orderId, WAREHOUSE_ID, productId, 4, "operator")));
        when(reservations.reservedQuantity(WAREHOUSE_ID, productId)).thenReturn(4L);

        assertThrows(BadRequestException.class, () -> service().consumeReservation(
                WAREHOUSE_ID, productId, 4, orderId, "operator"));

        assertEquals(3, item.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void restoreForOrderRecordsCancelledOrderMovement() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 6);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));

        service().restoreForOrder(WAREHOUSE_ID, productId, 4, orderId, "operator@example.com");

        assertEquals(10, item.getQuantity());
        assertMovement(StockMovementType.ORDER_CANCELLED, 4, 6, 10,
                orderId.toString(), "operator@example.com");
        verify(productCatalog).requireStoredProduct(productId);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void restoreForOrderRejectsNonPositiveQuantityWithoutInteractions(int quantity) {
        assertThrows(BadRequestException.class, () -> service().restoreForOrder(
                WAREHOUSE_ID, UUID.randomUUID(), quantity, UUID.randomUUID(), "operator"));

        verifyNoInteractions(productCatalog, repository, movementRepository);
    }

    @Test
    void restoreForOrderRejectsOverflowWithoutMovement() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, Integer.MAX_VALUE);
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));

        assertThrows(ConflictException.class, () -> service().restoreForOrder(
                WAREHOUSE_ID, productId, 1, UUID.randomUUID(), "operator"));

        assertEquals(Integer.MAX_VALUE, item.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void orderOperationPropagatesLockPersistenceErrorWithoutMovement() {
        UUID productId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("lock failed");
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service().consumeReservation(
                WAREHOUSE_ID, productId, 1, UUID.randomUUID(), "operator"));

        assertSame(failure, thrown);
        verifyNoInteractions(movementRepository);
    }

    @Test
    void movementPersistenceErrorIsPropagated() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 5);
        RuntimeException failure = new RuntimeException("movement insert failed");
        when(repository.findForUpdate(WAREHOUSE_ID, productId)).thenReturn(Optional.of(item));
        when(reservations.findForUpdate(orderId, WAREHOUSE_ID, productId))
                .thenReturn(Optional.of(new InventoryReservation(
                        orderId, WAREHOUSE_ID, productId, 2, "operator")));
        when(reservations.reservedQuantity(WAREHOUSE_ID, productId)).thenReturn(2L);
        when(movementRepository.save(any(StockMovement.class))).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service().consumeReservation(
                WAREHOUSE_ID, productId, 2, orderId, "operator"));

        assertSame(failure, thrown);
        verify(movementRepository).save(any(StockMovement.class));
    }

    private InventoryService service() {
        return new InventoryService(
                repository, reservations, movementRepository, productCatalog, warehouses);
    }

    private InventoryItem inventory(UUID productId, int quantity) {
        InventoryItem item = new InventoryItem(WAREHOUSE_ID, productId);
        item.changeQuantity(quantity);
        return item;
    }

    private InventoryBalanceProjection balance(
            UUID productId, int quantity, int reservedQuantity) {
        return new InventoryBalanceProjection() {
            @Override
            public UUID getWarehouseId() { return WAREHOUSE_ID; }

            @Override
            public UUID getProductId() { return productId; }

            @Override
            public int getQuantity() { return quantity; }

            @Override
            public int getReservedQuantity() { return reservedQuantity; }

            @Override
            public Instant getUpdatedAt() { return Instant.EPOCH; }
        };
    }

    private void assertMovement(StockMovementType type, int delta, int balanceBefore,
                                int balanceAfter, String businessReference,
                                String responsibleUser) {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(captor.capture());
        StockMovement movement = captor.getValue();
        assertEquals(type, field(movement, "movementType"));
        assertEquals(Integer.valueOf(delta), field(movement, "quantityDelta"));
        assertEquals(Integer.valueOf(balanceBefore), field(movement, "balanceBefore"));
        assertEquals(Integer.valueOf(balanceAfter), field(movement, "balanceAfter"));
        if (businessReference == null) {
            String reference = field(movement, "businessReference");
            assertTrue(reference.startsWith("MANUAL:"));
            UUID.fromString(reference.substring("MANUAL:".length()));
        } else {
            assertEquals(businessReference, field(movement, "businessReference"));
        }
        assertEquals(responsibleUser, field(movement, "responsibleUser"));
    }

    @SuppressWarnings("unchecked")
    private <T> T field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect field " + name, exception);
        }
    }
}
