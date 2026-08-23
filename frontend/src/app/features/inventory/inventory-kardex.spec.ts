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
import { InventoryKardex } from './inventory-kardex';

describe('InventoryKardex', () => {
  it('sends all URL filters remotely and renders physical/reserved effects with ID fallback', async () => {
    const productId = '10000000-0000-4000-8000-000000000001';
    const listMovements = vi.fn(() => of(movementPage(productId)));
    const harness = await createHarness({
      query: {
        page: '2',
        size: '25',
        productId,
        type: 'ORDER_RESERVED',
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-02T00:00:00Z',
        reference: 'ORDER-1',
      },
      listMovements,
    });
    harness.fixture.detectChanges();

    expect(listMovements).toHaveBeenCalledWith(MAIN_WAREHOUSE_ID, {
      page: 2,
      size: 25,
      productId,
      type: 'ORDER_RESERVED',
      from: '2026-08-01T00:00:00.000Z',
      to: '2026-08-02T00:00:00.000Z',
      reference: 'ORDER-1',
    });
    const text = harness.fixture.nativeElement.textContent as string;
    expect(text).toContain(productId);
    expect(text).toContain('Identificador histórico');
    expect(text).toContain('Reserva de pedido');
    expect(text).toContain('+3');
    expect(text).toContain('8 → 8');
  });

  it('rejects invalid product/reference and from-after-to without changing the URL', async () => {
    const harness = await createHarness();
    harness.router.navigate.mockClear();
    const form = harness.fixture.componentInstance['filterForm'];

    form.setValue({
      productId: 'not-a-uuid',
      type: '',
      from: '',
      to: '',
      reference: 'x'.repeat(129),
    });
    harness.fixture.componentInstance['applyFilters']();
    expect(harness.router.navigate).not.toHaveBeenCalled();

    form.setValue({
      productId: '',
      type: 'MANUAL_OUT',
      from: '2026-08-03T10:00',
      to: '2026-08-02T10:00',
      reference: ' MERMA-1 ',
    });
    harness.fixture.componentInstance['applyFilters']();
    expect(harness.router.navigate).not.toHaveBeenCalled();
    expect(harness.fixture.componentInstance['localError']()).toContain('fecha inicial');
  });

  it('cancels an obsolete warehouse response and rejects rows from the prior location visually', async () => {
    const data = new BehaviorSubject<Data>({ inventoryScope: 'warehouse' });
    const params = new BehaviorSubject<ParamMap>(convertToParamMap({ id: 'warehouse-north' }));
    const north = new Subject<ReturnType<typeof movementPage>>();
    const south = new Subject<ReturnType<typeof movementPage>>();
    const listMovements = vi.fn(
      (warehouseId: string): Observable<ReturnType<typeof movementPage>> =>
        warehouseId === 'warehouse-north' ? north : south,
    );
    const harness = await createHarness({ data, params, listMovements });

    params.next(convertToParamMap({ id: 'warehouse-south' }));
    expect(north.observed).toBe(false);
    north.next(movementPage('north-product', 'warehouse-north'));
    south.next(movementPage('south-product', 'warehouse-south'));
    south.complete();
    harness.fixture.detectChanges();

    expect(harness.fixture.nativeElement.textContent).toContain('south-product');
    expect(harness.fixture.nativeElement.textContent).not.toContain('north-product');
  });

  it.each([
    {
      result: of({ content: [], first: true, last: true }),
      expected: 'No hay movimientos con estos filtros',
    },
    {
      result: throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: { code: 'INTERNAL_ERROR', correlationId: 'kardex-error' },
          }),
      ),
      expected: 'Ocurrió un error inesperado',
    },
  ])('renders empty and recoverable error states', async ({ result, expected }) => {
    const harness = await createHarness({ listMovements: vi.fn(() => result) });
    harness.fixture.detectChanges();

    expect(harness.fixture.nativeElement.textContent).toContain(expected);
    if (expected.includes('error')) {
      expect(harness.fixture.nativeElement.textContent).toContain('Reintentar');
    }
  });
});

interface HarnessOptions {
  readonly query?: Record<string, string>;
  readonly data?: BehaviorSubject<Data>;
  readonly params?: BehaviorSubject<ParamMap>;
  readonly listMovements?: ReturnType<typeof vi.fn>;
}

async function createHarness(options: HarnessOptions = {}) {
  TestBed.resetTestingModule();
  const data = options.data ?? new BehaviorSubject<Data>({ inventoryScope: 'main' });
  const params = options.params ?? new BehaviorSubject<ParamMap>(convertToParamMap({}));
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap(options.query ?? {}));
  const inventory = {
    listMovements: options.listMovements ?? vi.fn(() => of(movementPage())),
  };
  const warehouses = {
    get: vi.fn((warehouseId: string) =>
      of({ id: warehouseId, code: warehouseId, name: warehouseId, active: true }),
    ),
  };
  await TestBed.configureTestingModule({
    imports: [InventoryKardex],
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
  const router = TestBed.inject(Router) as Router & { navigate: ReturnType<typeof vi.fn> };
  vi.spyOn(router, 'navigate').mockResolvedValue(true);
  const fixture: ComponentFixture<InventoryKardex> = TestBed.createComponent(InventoryKardex);
  fixture.detectChanges();
  return { fixture, inventory, warehouses, router, data, params, queryParams };
}

function movementPage(productId = 'deleted-product-id', warehouseId = MAIN_WAREHOUSE_ID) {
  return {
    content: [
      {
        id: 'movement-a',
        warehouseId,
        productId,
        movementType: 'ORDER_RESERVED' as const,
        quantityDelta: 0,
        balanceBefore: 8,
        balanceAfter: 8,
        reservationDelta: 3,
        reservedBefore: 0,
        reservedAfter: 3,
        businessReference: 'ORDER-1',
        occurredAt: '2026-08-23T12:00:00Z',
        responsibleUser: 'operator-id',
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
