package com.example.inventory.products;

import com.example.inventory.shared.ConflictException;
import com.example.inventory.shared.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Test
    void findAllRequestsProductsSortedByName() {
        Product keyboard = product("KEY-1", "Keyboard");
        Product mouse = product("MOU-1", "Mouse");
        when(repository.findAllByDeletedFalse(any(Sort.class))).thenReturn(List.of(keyboard, mouse));

        List<ProductResponse> responses = service().findAll();

        assertEquals(List.of("Keyboard", "Mouse"),
                responses.stream().map(ProductResponse::name).toList());
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(repository).findAllByDeletedFalse(sort.capture());
        assertEquals(Sort.by("name").ascending(), sort.getValue());
    }

    @Test
    void findByIdReturnsExistingProduct() {
        Product product = product("SKU-1", "Product");
        when(repository.findByIdAndDeletedFalse(product.getId())).thenReturn(Optional.of(product));

        ProductResponse response = service().findById(product.getId());

        assertEquals(product.getId(), response.id());
        assertEquals("SKU-1", response.sku());
    }

    @Test
    void findByIdRejectsMissingProduct() {
        UUID productId = UUID.randomUUID();
        when(repository.findByIdAndDeletedFalse(productId)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service().findById(productId));

        assertEquals("Product %s was not found".formatted(productId), error.getMessage());
    }

    @Test
    void createNormalizesProductFieldsBeforeSaving() {
        ProductRequest request = request("  sku-1  ", "  Keyboard  ", "  Compact  ");
        when(repository.existsBySkuIgnoreCase("SKU-1")).thenReturn(false);
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service().create(request);

        assertEquals("SKU-1", response.sku());
        assertEquals("Keyboard", response.name());
        assertEquals("Compact", response.description());
        verify(repository).save(any(Product.class));
    }

    @Test
    void createConvertsBlankDescriptionToNull() {
        ProductRequest request = request("SKU-1", "Keyboard", "   ");
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service().create(request);

        assertNull(response.description());
    }

    @Test
    void createRejectsDuplicateSkuWithoutSaving() {
        ProductRequest request = request(" sku-1 ", "Keyboard", null);
        when(repository.existsBySkuIgnoreCase("SKU-1")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service().create(request));

        assertEquals("SKU SKU-1 already exists", error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createPropagatesSkuLookupPersistenceErrorWithoutSaving() {
        ProductRequest request = request("SKU-1", "Keyboard", null);
        RuntimeException failure = new RuntimeException("lookup failed");
        when(repository.existsBySkuIgnoreCase("SKU-1")).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service().create(request));

        assertSame(failure, thrown);
        verify(repository, never()).save(any());
    }

    @Test
    void createPropagatesSavePersistenceError() {
        ProductRequest request = request("SKU-1", "Keyboard", null);
        RuntimeException failure = new RuntimeException("insert failed");
        when(repository.save(any(Product.class))).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service().create(request));

        assertSame(failure, thrown);
    }

    @Test
    void updateNormalizesFieldsAndExcludesCurrentProductFromSkuCheck() {
        Product product = product("OLD", "Old name");
        ProductRequest request = request(" new-sku ", " New name ", " New description ");
        when(repository.findByIdAndDeletedFalse(product.getId())).thenReturn(Optional.of(product));
        when(repository.existsBySkuIgnoreCaseAndIdNot("NEW-SKU", product.getId())).thenReturn(false);

        ProductResponse response = service().update(product.getId(), request);

        assertEquals("NEW-SKU", response.sku());
        assertEquals("New name", response.name());
        assertEquals("New description", response.description());
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsDuplicateSkuWithoutChangingProduct() {
        Product product = product("ORIGINAL", "Original name");
        ProductRequest request = request("TAKEN", "Changed", null);
        when(repository.findByIdAndDeletedFalse(product.getId())).thenReturn(Optional.of(product));
        when(repository.existsBySkuIgnoreCaseAndIdNot("TAKEN", product.getId())).thenReturn(true);

        assertThrows(ConflictException.class, () -> service().update(product.getId(), request));

        assertEquals("ORIGINAL", product.getSku());
        assertEquals("Original name", product.getName());
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsMissingProductBeforeCheckingSku() {
        UUID productId = UUID.randomUUID();
        when(repository.findByIdAndDeletedFalse(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service().update(productId, request("SKU", "Name", null)));

        verifyNoMoreInteractions(repository);
    }

    @Test
    void deleteMarksExistingProductAsDeleted() {
        Product product = spy(product("SKU-1", "Product"));
        when(repository.findByIdAndDeletedFalse(product.getId())).thenReturn(Optional.of(product));

        service().delete(product.getId());

        verify(product).markDeleted();
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRejectsMissingProduct() {
        UUID productId = UUID.randomUUID();
        when(repository.findByIdAndDeletedFalse(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().delete(productId));

        verify(repository, never()).delete(any());
    }

    @Test
    void requireProductAcceptsExistingNonDeletedProduct() {
        UUID productId = UUID.randomUUID();
        when(repository.existsByIdAndDeletedFalse(productId)).thenReturn(true);

        service().requireProduct(productId);

        verify(repository).existsByIdAndDeletedFalse(productId);
    }

    @Test
    void requireProductRejectsMissingOrDeletedProduct() {
        UUID productId = UUID.randomUUID();

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service().requireProduct(productId));

        assertEquals("Product %s was not found".formatted(productId), error.getMessage());
    }

    @Test
    void requireProductPropagatesPersistenceError() {
        UUID productId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("database unavailable");
        when(repository.existsByIdAndDeletedFalse(productId)).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class, () -> service().requireProduct(productId));

        assertSame(failure, thrown);
    }

    private ProductService service() {
        return new ProductService(repository);
    }

    private Product product(String sku, String name) {
        return new Product(sku, name, null, new BigDecimal("10.00"), true);
    }

    private ProductRequest request(String sku, String name, String description) {
        return new ProductRequest(sku, name, description, new BigDecimal("10.00"), true);
    }
}
