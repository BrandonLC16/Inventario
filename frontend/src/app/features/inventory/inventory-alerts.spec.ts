import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  Data,
  ParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';

import { InventoryApiAdapter, MAIN_WAREHOUSE_ID } from '../../core/api/inventory-api.adapter';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { InventoryAlerts } from './inventory-alerts';

describe('InventoryAlerts', () => {
  it('uses MAIN server filters and renders alert level and replenishment without metadata calls', async () => {
    const response = new Subject<ReturnType<typeof alertPage>>();
    const harness = await createHarness({
      query: { page: '1', size: '10', search: ' tornillo ', outOfStockOnly: 'true' },
      listLowStock: vi.fn(() => response),
    });

    expect(harness.inventory.listLowStock).toHaveBeenCalledWith(MAIN_WAREHOUSE_ID, {
      page: 1,
      size: 10,
      search: 'tornillo',
      outOfStockOnly: true,
    });
    expect(harness.warehouses.get).not.toHaveBeenCalled();
    response.next(alertPage());
    response.complete();
    harness.fixture.detectChanges();

    const text = harness.fixture.nativeElement.textContent as string;
    expect(text).toContain('OUT-001');
    expect(text).toContain('Agotado');
    expect(text).toContain('5');

    harness.queryParams.next(
      convertToParamMap({ page: '1', size: '10', search: 'tornillo', outOfStockOnly: 'true' }),
    );
    harness.fixture.detectChanges();
    expect(harness.inventory.listLowStock).toHaveBeenCalledTimes(1);
    expect(harness.fixture.nativeElement.textContent).toContain('OUT-001');
  });

  it('cancels an obsolete location/filter response and keeps filters in router navigation', async () => {
    const first = new Subject<ReturnType<typeof alertPage>>();
    const second = new Subject<ReturnType<typeof alertPage>>();
    const listLowStock = vi.fn(
      (
        _warehouseId: string,
        request: { search?: string },
      ): Observable<ReturnType<typeof alertPage>> => (request.search === 'nuevo' ? second : first),
    );
    const harness = await createHarness({ listLowStock });

    harness.fixture.componentInstance['filterForm'].setValue({
      search: ' nuevo ',
      outOfStockOnly: true,
    });
    harness.fixture.componentInstance['applyFilters']();
    expect(harness.router.navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: { search: 'nuevo', outOfStockOnly: true },
    });

    harness.queryParams.next(convertToParamMap({ search: 'nuevo', outOfStockOnly: 'true' }));
    expect(first.observed).toBe(false);
    first.next(alertPage('obsolete-product'));
    second.next(alertPage('current-product'));
    second.complete();
    harness.fixture.detectChanges();

    expect(harness.fixture.nativeElement.textContent).toContain('SKU-current-product');
    expect(harness.fixture.nativeElement.textContent).not.toContain('SKU-obsolete-product');
  });

  it.each([
    {
      result: of({ content: [], first: true, last: true }),
      expected: 'No hay alertas con estos filtros',
    },
    {
      result: throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: { code: 'INTERNAL_ERROR', correlationId: 'alerts-error' },
          }),
      ),
      expected: 'Ocurrió un error inesperado',
    },
  ])('renders empty and recoverable error states', async ({ result, expected }) => {
    const harness = await createHarness({ listLowStock: vi.fn(() => result) });
    harness.fixture.detectChanges();

    expect(harness.fixture.nativeElement.textContent).toContain(expected);
    if (expected.includes('error')) {
      expect(harness.fixture.nativeElement.textContent).toContain('Reintentar');
    }
  });
});

interface HarnessOptions {
  readonly query?: Record<string, string>;
  readonly listLowStock?: ReturnType<typeof vi.fn>;
}

async function createHarness(options: HarnessOptions = {}) {
  TestBed.resetTestingModule();
  const data = new BehaviorSubject<Data>({ inventoryScope: 'main' });
  const params = new BehaviorSubject<ParamMap>(convertToParamMap({}));
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap(options.query ?? {}));
  const inventory = {
    listLowStock: options.listLowStock ?? vi.fn(() => of(alertPage())),
  };
  const warehouses = { get: vi.fn() };
  await TestBed.configureTestingModule({
    imports: [InventoryAlerts],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          data: data.asObservable(),
          paramMap: params.asObservable(),
          queryParamMap: queryParams.asObservable(),
          snapshot: { queryParamMap: queryParams.value },
        },
      },
      { provide: InventoryApiAdapter, useValue: inventory },
      { provide: WarehousesApiAdapter, useValue: warehouses },
    ],
  }).compileComponents();
  const router = TestBed.inject(Router);
  vi.spyOn(router, 'navigate').mockResolvedValue(true);
  const fixture: ComponentFixture<InventoryAlerts> = TestBed.createComponent(InventoryAlerts);
  fixture.detectChanges();
  return { fixture, inventory, warehouses, router, data, params, queryParams };
}

function alertPage(productId = 'product-a') {
  return {
    content: [
      {
        warehouseId: MAIN_WAREHOUSE_ID,
        productId,
        sku: productId === 'product-a' ? 'OUT-001' : `SKU-${productId}`,
        name: `Producto ${productId}`,
        quantity: 3,
        reservedQuantity: 3,
        availableQuantity: 0,
        minimumStock: 5,
        replenishmentQuantity: 5,
        alert: 'OUT_OF_STOCK' as const,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  };
}
