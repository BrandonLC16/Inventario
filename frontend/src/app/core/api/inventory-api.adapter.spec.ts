import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Configuration } from './generated/configuration';
import {
  InventoryApiAdapter,
  InventoryBalancePage,
  MAIN_WAREHOUSE_ID,
} from './inventory-api.adapter';

describe('InventoryApiAdapter', () => {
  let adapter: InventoryApiAdapter;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: Configuration,
          useValue: new Configuration({
            basePath: 'https://api.example.test',
            withCredentials: false,
          }),
        },
      ],
    });
    adapter = TestBed.inject(InventoryApiAdapter);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('composes a MAIN page by productId with two requests even when metadata is reordered', () => {
    let result: InventoryBalancePage | undefined;
    adapter.listMain({ page: 2, size: 25 }).subscribe((value) => {
      result = value;
    });

    const balances = httpTesting.expectOne(
      (request) => request.url === 'https://api.example.test/api/v1/inventory',
    );
    const metadata = httpTesting.expectOne(
      (request) =>
        request.url ===
        `https://api.example.test/api/v1/warehouses/${MAIN_WAREHOUSE_ID}/inventory/settings`,
    );
    for (const request of [balances, metadata]) {
      expect(request.request.method).toBe('GET');
      expect(request.request.params.get('page')).toBe('2');
      expect(request.request.params.get('size')).toBe('25');
      expect(request.request.withCredentials).toBe(false);
    }

    balances.flush({
      content: [
        balance('product-a', MAIN_WAREHOUSE_ID, 8, 3, 5),
        balance('product-b', MAIN_WAREHOUSE_ID, 0, 0, 0),
      ],
      page: 2,
      size: 25,
      totalElements: 2,
      totalPages: 1,
    });
    metadata.flush({
      content: [
        product('product-b', MAIN_WAREHOUSE_ID, 'SKU-B', 'Producto B'),
        product('product-a', MAIN_WAREHOUSE_ID, 'SKU-A', 'Producto A'),
      ],
      page: 2,
      size: 25,
      totalElements: 2,
      totalPages: 1,
    });

    expect(result).toMatchObject({
      rows: [
        { balance: { productId: 'product-a' }, product: { sku: 'SKU-A' } },
        { balance: { productId: 'product-b', quantity: 0 }, product: { sku: 'SKU-B' } },
      ],
    });
  });

  it('uses the scoped endpoint and rejects a balance leaked from another warehouse', () => {
    const expectedWarehouse = 'warehouse-north';
    let receivedError: unknown;
    adapter.listWarehouse(expectedWarehouse, { page: 0, size: 20 }).subscribe({
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    const balances = httpTesting.expectOne(
      `https://api.example.test/api/v1/warehouses/${expectedWarehouse}/inventory?page=0&size=20`,
    );
    const metadata = httpTesting.expectOne(
      `https://api.example.test/api/v1/warehouses/${expectedWarehouse}/inventory/settings?page=0&size=20`,
    );
    balances.flush({
      content: [balance('product-a', 'warehouse-south', 4, 1, 3)],
      totalElements: 1,
    });
    metadata.flush({
      content: [product('product-a', expectedWarehouse, 'SKU-A', 'Producto A')],
      totalElements: 1,
    });

    expect(receivedError).toBeInstanceOf(Error);
  });

  it('rejects missing metadata and inconsistent physical/reserved/available quantities', () => {
    let missingMetadataError: unknown;
    adapter.listMain().subscribe({
      error: (error: unknown) => {
        missingMetadataError = error;
      },
    });
    httpTesting.expectOne('https://api.example.test/api/v1/inventory').flush({
      content: [balance('product-a', MAIN_WAREHOUSE_ID, 2, 0, 2)],
      totalElements: 1,
    });
    httpTesting
      .expectOne(
        `https://api.example.test/api/v1/warehouses/${MAIN_WAREHOUSE_ID}/inventory/settings`,
      )
      .flush({ content: [], totalElements: 1 });
    expect(missingMetadataError).toBeInstanceOf(Error);

    let inconsistentError: unknown;
    adapter.listMain().subscribe({
      error: (error: unknown) => {
        inconsistentError = error;
      },
    });
    httpTesting.expectOne('https://api.example.test/api/v1/inventory').flush({
      content: [balance('product-a', MAIN_WAREHOUSE_ID, 2, 3, -1)],
      totalElements: 1,
    });
    httpTesting
      .expectOne(
        `https://api.example.test/api/v1/warehouses/${MAIN_WAREHOUSE_ID}/inventory/settings`,
      )
      .flush({
        content: [product('product-a', MAIN_WAREHOUSE_ID, 'SKU-A', 'Producto A')],
        totalElements: 1,
      });
    expect(inconsistentError).toBeInstanceOf(Error);
  });
});

function balance(
  productId: string,
  warehouseId: string,
  quantity: number,
  reservedQuantity: number,
  availableQuantity: number,
) {
  return { productId, warehouseId, quantity, reservedQuantity, availableQuantity };
}

function product(productId: string, warehouseId: string, sku: string, name: string) {
  return { productId, warehouseId, sku, name, active: true, minimumStock: 0 };
}
