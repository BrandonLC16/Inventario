import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Configuration } from './generated/configuration';
import { DISABLE_AUTH_REPLAY } from '../session/session.interceptor';
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

  it('lists settings only for the requested warehouse and preserves zero', () => {
    let result: number | undefined;
    adapter.listSettings('warehouse-north', { page: 1, size: 10 }).subscribe((page) => {
      result = page.content?.[0]?.minimumStock;
    });

    const request = httpTesting.expectOne(
      'https://api.example.test/api/v1/warehouses/warehouse-north/inventory/settings?page=1&size=10',
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      content: [product('product-a', 'warehouse-north', 'SKU-A', 'Producto A', 0, false)],
    });

    expect(result).toBe(0);
  });

  it('rejects settings leaked from another warehouse', () => {
    let receivedError: unknown;
    adapter.listSettings('warehouse-north').subscribe({
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    httpTesting
      .expectOne('https://api.example.test/api/v1/warehouses/warehouse-north/inventory/settings')
      .flush({
        content: [product('product-a', 'warehouse-south', 'SKU-A', 'Producto A')],
      });

    expect(receivedError).toBeInstanceOf(Error);
  });

  it('re-reads and validates one setting only after configure returns 204', () => {
    let result: unknown;
    adapter
      .configureSetting('warehouse-north', 'product-a', { minimumStock: 7, active: false })
      .subscribe((setting) => {
        result = setting;
      });

    const update = httpTesting.expectOne(
      'https://api.example.test/api/v1/warehouses/warehouse-north/inventory/product-a/settings',
    );
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ minimumStock: 7, active: false });
    httpTesting.expectNone(
      'https://api.example.test/api/v1/warehouses/warehouse-north/inventory/product-a/settings',
    );
    update.flush(null, { status: 204, statusText: 'No Content' });

    const refresh = httpTesting.expectOne(
      'https://api.example.test/api/v1/warehouses/warehouse-north/inventory/product-a/settings',
    );
    expect(refresh.request.method).toBe('GET');
    refresh.flush(product('product-a', 'warehouse-north', 'SKU-A', 'Producto A', 7, false));

    expect(result).toMatchObject({ minimumStock: 7, active: false });
  });

  it('uses the MAIN alias once and reconciles only from its response', () => {
    let result: unknown;
    adapter
      .adjustStock(MAIN_WAREHOUSE_ID, 'product-a', { quantityDelta: -2, reference: ' MERMA ' })
      .subscribe((balance) => {
        result = balance;
      });

    const request = httpTesting.expectOne(
      'https://api.example.test/api/v1/inventory/product-a/adjustments',
    );
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ quantityDelta: -2, reference: ' MERMA ' });
    expect(request.request.context.get(DISABLE_AUTH_REPLAY)).toBe(true);
    expect(result).toBeUndefined();
    request.flush(balance('product-a', MAIN_WAREHOUSE_ID, 6, 3, 3));

    expect(result).toMatchObject({ quantity: 6, reservedQuantity: 3, availableQuantity: 3 });
  });

  it('uses the warehouse route and never retries a failed mutation', () => {
    let receivedError: unknown;
    adapter
      .adjustStock('warehouse-north', 'product-a', { quantityDelta: 4 })
      .subscribe({ error: (error: unknown) => (receivedError = error) });

    const request = httpTesting.expectOne(
      'https://api.example.test/api/v1/warehouses/warehouse-north/inventory/product-a/adjustments',
    );
    expect(request.request.method).toBe('PATCH');
    request.flush(null, { status: 0, statusText: 'Network error' });

    expect(receivedError).toBeTruthy();
    httpTesting.expectNone(
      'https://api.example.test/api/v1/warehouses/warehouse-north/inventory/product-a/adjustments',
    );
  });

  it('rejects an adjustment response from another warehouse or product', () => {
    let receivedError: unknown;
    adapter
      .adjustStock('warehouse-north', 'product-a', { quantityDelta: 1 })
      .subscribe({ error: (error: unknown) => (receivedError = error) });

    httpTesting
      .expectOne(
        'https://api.example.test/api/v1/warehouses/warehouse-north/inventory/product-a/adjustments',
      )
      .flush(balance('product-b', 'warehouse-north', 1, 0, 1));

    expect(receivedError).toBeInstanceOf(Error);
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

function product(
  productId: string,
  warehouseId: string,
  sku: string,
  name: string,
  minimumStock = 0,
  active = true,
) {
  return { productId, warehouseId, sku, name, active, minimumStock };
}
