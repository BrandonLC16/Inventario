package com.example.inventory.counts;

import com.example.inventory.inventory.InventoryOperations;
import com.example.inventory.products.ProductCatalog;
import com.example.inventory.shared.ConflictException;
import com.example.inventory.warehouses.WarehouseDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCountServiceTest {

    @Mock private InventoryCountRepository counts;
    @Mock private InventoryCountLineRepository countLines;
    @Mock private ProductCatalog products;
    @Mock private WarehouseDirectory warehouses;
    @Mock private InventoryOperations inventory;

    @Test
    void fullCountReadsOnlyEnoughProductsToDetectTheLimit() {
        UUID warehouseId = UUID.randomUUID();
        List<UUID> productIds = IntStream.rangeClosed(0, 1000)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        when(warehouses.productIdsForPhysicalCount(warehouseId, 1001))
                .thenReturn(productIds);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service().create(new CreateInventoryCountRequest(
                        warehouseId, InventoryCountScope.FULL, List.of())));

        assertEquals(
                "A full inventory count cannot contain more than 1000 products; use selected inventory counts",
                error.getMessage());
        verify(warehouses).productIdsForPhysicalCount(warehouseId, 1001);
        verify(counts, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(products, inventory);
    }

    @Test
    void oversizedLegacyCountCannotStartAnUnboundedOpeningTransaction() {
        UUID warehouseId = UUID.randomUUID();
        InventoryCount count = new InventoryCount("CNT-0000000001", warehouseId,
                InventoryCountScope.FULL, List.of(UUID.randomUUID()));
        when(counts.findByIdForUpdate(count.getId())).thenReturn(java.util.Optional.of(count));
        when(countLines.countByCountId(count.getId())).thenReturn(1001L);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service().open(count.getId(), "operator"));

        assertEquals("An inventory count cannot contain more than 1000 products",
                error.getMessage());
        verify(countLines, never()).findByCountIdForUpdate(count.getId());
        verifyNoInteractions(products, inventory);
    }

    private InventoryCountService service() {
        return new InventoryCountService(
                counts, countLines, products, warehouses, inventory);
    }
}
