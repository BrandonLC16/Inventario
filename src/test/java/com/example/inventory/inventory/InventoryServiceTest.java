package com.example.inventory.inventory;

import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.BadRequestException;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
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

    @Mock
    private InventoryRepository repository;

    @Mock
    private InventoryReservationRepository reservations;

    @Mock
    private StockMovementRepository movementRepository;

    @Mock
    private ProductCatalog productCatalog;

    @Test
    void findByProductIdReturnsExistingInventory() {
        UUID productId = UUID.randomUUID();
        when(repository.findBalance(productId))
                .thenReturn(Optional.of(balance(productId, 7, 2)));

        InventoryResponse response = service().findByProductId(productId);

        assertEquals(productId, response.productId());
        assertEquals(7, response.quantity());
        assertEquals(2, response.reservedQuantity());
        assertEquals(5, response.availableQuantity());
        verify(productCatalog).requireProduct(productId);
    }

    @Test
    void findByProductIdReturnsZeroWhenInventoryDoesNotExist() {
        UUID productId = UUID.randomUUID();
        when(repository.findBalance(productId)).thenReturn(Optional.empty());

        InventoryResponse response = service().findByProductId(productId);

        assertEquals(productId, response.productId());
        assertEquals(0, response.quantity());
    }

    @Test
    void findByProductIdStopsWhenProductDoesNotExist() {
        UUID productId = UUID.randomUUID();
        doThrow(new NotFoundException("missing")).when(productCatalog).requireProduct(productId);

        assertThrows(NotFoundException.class, () -> service().findByProductId(productId));

        verifyNoInteractions(repository, movementRepository);
    }

    @Test
    void firstPositiveAdjustmentRecordsInitialStock() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 0);
        when(repository.ensureExists(productId)).thenReturn(1);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));

        InventoryResponse response = service().adjust(productId, 8, "operator");

        assertEquals(8, response.quantity());
        assertMovement(StockMovementType.INITIAL_STOCK, 8, 0, 8, null, "operator");
    }

    @Test
    void laterPositiveAdjustmentRecordsManualInput() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 4);
        when(repository.ensureExists(productId)).thenReturn(0);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));

        InventoryResponse response = service().adjust(productId, 3, "operator");

        assertEquals(7, response.quantity());
        assertMovement(StockMovementType.MANUAL_IN, 3, 4, 7, null, "operator");
    }

    @Test
    void validNegativeAdjustmentRecordsManualOutput() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 9);
        when(repository.ensureExists(productId)).thenReturn(0);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));

        InventoryResponse response = service().adjust(productId, -5, "operator");

        assertEquals(4, response.quantity());
        assertMovement(StockMovementType.MANUAL_OUT, -5, 9, 4, null, "operator");
    }

    @Test
    void zeroAdjustmentIsRejectedWithoutRecordingMovement() {
        UUID productId = UUID.randomUUID();

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service().adjust(productId, 0, "operator"));

        assertEquals("Inventory adjustment must not be zero", error.getMessage());
        verify(repository, never()).ensureExists(any());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void adjustmentRequiresResponsibleUserBeforeTouchingInventory() {
        assertThrows(IllegalArgumentException.class,
                () -> service().adjust(UUID.randomUUID(), 1, " "));

        verifyNoInteractions(productCatalog, repository, movementRepository);
    }

    @Test
    void insufficientStockIsRejectedWithoutChangingBalanceOrRecordingMovement() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 2);
        when(repository.ensureExists(productId)).thenReturn(0);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service().adjust(productId, -3, "operator"));

        assertEquals("Inventory quantity cannot be negative", error.getMessage());
        assertEquals(2, item.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void adjustmentOverflowIsRejectedWithoutChangingBalanceOrRecordingMovement() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, Integer.MAX_VALUE);
        when(repository.ensureExists(productId)).thenReturn(0);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service().adjust(productId, 1, "operator"));

        assertEquals("Inventory quantity is outside the supported range", error.getMessage());
        assertEquals(Integer.MAX_VALUE, item.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void adjustmentForMissingProductDoesNotTouchInventoryOrMovements() {
        UUID productId = UUID.randomUUID();
        doThrow(new NotFoundException("missing")).when(productCatalog).requireProduct(productId);

        assertThrows(NotFoundException.class, () -> service().adjust(productId, 1, "operator"));

        verifyNoInteractions(repository, movementRepository);
    }

    @Test
    void adjustmentFailsWithoutMovementWhenInventoryCannotBeLocked() {
        UUID productId = UUID.randomUUID();
        when(repository.ensureExists(productId)).thenReturn(1);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> service().adjust(productId, 1, "operator"));

        verifyNoInteractions(movementRepository);
    }

    @Test
    void adjustmentPropagatesInitializationPersistenceErrorWithoutMovement() {
        UUID productId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("database unavailable");
        when(repository.ensureExists(productId)).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class, () -> service().adjust(productId, 1, "operator"));

        assertSame(failure, thrown);
        verifyNoInteractions(movementRepository);
    }

    @Test
    void consumeReservationRecordsConfirmedOrderMovement() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 10);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));
        when(reservations.findForUpdate(orderId, productId))
                .thenReturn(Optional.of(new InventoryReservation(
                        orderId, productId, 4, "operator@example.com")));
        when(reservations.reservedQuantity(productId)).thenReturn(4L);

        service().consumeReservation(productId, 4, orderId, "operator@example.com");

        assertEquals(6, item.getQuantity());
        assertMovement(StockMovementType.ORDER_CONFIRMED, -4, 10, 6,
                orderId.toString(), "operator@example.com");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void consumeReservationRejectsNonPositiveQuantityWithoutInteractions(int quantity) {
        assertThrows(BadRequestException.class, () -> service().consumeReservation(
                UUID.randomUUID(), quantity, UUID.randomUUID(), "operator"));

        verifyNoInteractions(productCatalog, repository, movementRepository);
    }

    @Test
    void consumeReservationRejectsMissingProductWithoutMovement() {
        UUID productId = UUID.randomUUID();
        doThrow(new NotFoundException("missing"))
                .when(productCatalog).requireStoredProduct(productId);

        assertThrows(NotFoundException.class, () -> service().consumeReservation(
                productId, 1, UUID.randomUUID(), "operator"));

        verifyNoInteractions(repository, movementRepository);
    }

    @Test
    void consumeReservationRejectsInsufficientStockWithoutMovement() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 3);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));
        when(reservations.findForUpdate(orderId, productId))
                .thenReturn(Optional.of(new InventoryReservation(
                        orderId, productId, 4, "operator")));
        when(reservations.reservedQuantity(productId)).thenReturn(4L);

        assertThrows(BadRequestException.class, () -> service().consumeReservation(
                productId, 4, orderId, "operator"));

        assertEquals(3, item.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void restoreForOrderRecordsCancelledOrderMovement() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 6);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));

        service().restoreForOrder(productId, 4, orderId, "operator@example.com");

        assertEquals(10, item.getQuantity());
        assertMovement(StockMovementType.ORDER_CANCELLED, 4, 6, 10,
                orderId.toString(), "operator@example.com");
        verify(productCatalog).requireStoredProduct(productId);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void restoreForOrderRejectsNonPositiveQuantityWithoutInteractions(int quantity) {
        assertThrows(BadRequestException.class, () -> service().restoreForOrder(
                UUID.randomUUID(), quantity, UUID.randomUUID(), "operator"));

        verifyNoInteractions(productCatalog, repository, movementRepository);
    }

    @Test
    void restoreForOrderRejectsOverflowWithoutMovement() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = inventory(productId, Integer.MAX_VALUE);
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));

        assertThrows(ConflictException.class, () -> service().restoreForOrder(
                productId, 1, UUID.randomUUID(), "operator"));

        assertEquals(Integer.MAX_VALUE, item.getQuantity());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void orderOperationPropagatesLockPersistenceErrorWithoutMovement() {
        UUID productId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("lock failed");
        when(repository.findByProductIdForUpdate(productId)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service().consumeReservation(
                productId, 1, UUID.randomUUID(), "operator"));

        assertSame(failure, thrown);
        verifyNoInteractions(movementRepository);
    }

    @Test
    void movementPersistenceErrorIsPropagated() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventory(productId, 5);
        RuntimeException failure = new RuntimeException("movement insert failed");
        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));
        when(reservations.findForUpdate(orderId, productId))
                .thenReturn(Optional.of(new InventoryReservation(
                        orderId, productId, 2, "operator")));
        when(reservations.reservedQuantity(productId)).thenReturn(2L);
        when(movementRepository.save(any(StockMovement.class))).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service().consumeReservation(
                productId, 2, orderId, "operator"));

        assertSame(failure, thrown);
        verify(movementRepository).save(any(StockMovement.class));
    }

    private InventoryService service() {
        return new InventoryService(
                repository, reservations, movementRepository, productCatalog);
    }

    private InventoryItem inventory(UUID productId, int quantity) {
        InventoryItem item = new InventoryItem(productId);
        item.changeQuantity(quantity);
        return item;
    }

    private InventoryBalanceProjection balance(
            UUID productId, int quantity, int reservedQuantity) {
        return new InventoryBalanceProjection() {
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
