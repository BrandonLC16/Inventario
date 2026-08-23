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

import {
  InventoryApiAdapter,
  InventoryBalancePage,
  MAIN_WAREHOUSE_ID,
} from '../../core/api/inventory-api.adapter';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { SessionService } from '../../core/session/session.service';
import { InventoryBalances } from './inventory-balances';

describe('InventoryBalances MAIN', () => {
  let fixture: ComponentFixture<InventoryBalances>;
  let queryParams: BehaviorSubject<ParamMap>;
  let mainResult: Subject<InventoryBalancePage>;
  let inventoryAdapter: {
    listMain: ReturnType<typeof vi.fn>;
    listWarehouse: ReturnType<typeof vi.fn>;
  };
  let warehousesAdapter: {
    list: ReturnType<typeof vi.fn>;
    get: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    queryParams = new BehaviorSubject(convertToParamMap({}));
    mainResult = new Subject();
    inventoryAdapter = {
      listMain: vi.fn(() => mainResult),
      listWarehouse: vi.fn(),
    };
    warehousesAdapter = {
      list: vi.fn(() =>
        of({
          content: [
            { id: MAIN_WAREHOUSE_ID, code: 'MAIN', name: 'Principal', active: true },
            { id: 'warehouse-north', code: 'NORTH', name: 'Norte', active: true },
          ],
          page: 0,
          first: true,
          last: true,
        }),
      ),
      get: vi.fn(),
    };
    await configureComponent(
      new BehaviorSubject<Data>({ inventoryScope: 'main' }),
      new BehaviorSubject(convertToParamMap({})),
      queryParams,
      inventoryAdapter,
      warehousesAdapter,
    );
    fixture = TestBed.createComponent(InventoryBalances);
    fixture.detectChanges();
  });

  it('labels the compatibility alias and renders zero/null without inventing movement data', () => {
    expect(inventoryAdapter.listMain).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(inventoryAdapter.listWarehouse).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Cargando inventario de MAIN');

    mainResult.next(balancePage('product-zero', MAIN_WAREHOUSE_ID, 0, 0, 0));
    mainResult.complete();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('MAIN es un almacén, no un total multi-almacén');
    expect(text).toContain('/api/v1/inventory');
    expect(text).toContain('SKU-product-zero');
    expect(text).toContain('Sin movimientos');
    expect(fixture.nativeElement.querySelectorAll('[aria-label^="Ajustar stock de"]')).toHaveLength(
      0,
    );
    const cells = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLTableCellElement>('tbody td'),
    );
    expect(cells.slice(2, 5).map((cell) => cell.textContent?.trim())).toEqual(['0', '0', '0']);
  });

  it('pages balances remotely and does not reload them when only the selector page changes', () => {
    const secondPage = new Subject<InventoryBalancePage>();
    inventoryAdapter.listMain.mockReturnValue(secondPage);

    queryParams.next(convertToParamMap({ page: '2', size: '25' }));
    expect(inventoryAdapter.listMain).toHaveBeenLastCalledWith({ page: 2, size: 25 });
    expect(mainResult.observed).toBe(false);

    const balanceCalls = inventoryAdapter.listMain.mock.calls.length;
    queryParams.next(convertToParamMap({ page: '2', size: '25', warehousePage: '1' }));

    expect(inventoryAdapter.listMain).toHaveBeenCalledTimes(balanceCalls);
    expect(warehousesAdapter.list).toHaveBeenLastCalledWith({ page: 1, size: 20 });
  });

  it('renders an API error as recoverable and then an empty state', () => {
    inventoryAdapter.listMain
      .mockReturnValueOnce(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 503,
              error: { code: 'INTERNAL_ERROR', correlationId: 'inventory-retry' },
            }),
        ),
      )
      .mockReturnValueOnce(of({ response: { content: [] }, rows: [] }));

    fixture.componentInstance['retryBalances']();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ocurrió un error inesperado');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');

    fixture.componentInstance['retryBalances']();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay productos para mostrar');
  });
});

describe('InventoryBalances warehouse isolation', () => {
  it('cancels an obsolete warehouse response and never retains rows from the previous location', async () => {
    const data = new BehaviorSubject<Data>({ inventoryScope: 'warehouse' });
    const params = new BehaviorSubject(convertToParamMap({ id: 'warehouse-north' }));
    const queryParams = new BehaviorSubject(convertToParamMap({}));
    const north = new Subject<InventoryBalancePage>();
    const south = new Subject<InventoryBalancePage>();
    const inventoryAdapter = {
      listMain: vi.fn(),
      listWarehouse: vi.fn(
        (warehouseId: string): Observable<InventoryBalancePage> =>
          warehouseId === 'warehouse-north' ? north : south,
      ),
    };
    const warehousesAdapter = {
      list: vi.fn(() => of({ content: [], first: true, last: true })),
      get: vi.fn((warehouseId: string) =>
        of({ id: warehouseId, code: warehouseId, name: warehouseId, active: true }),
      ),
    };
    await configureComponent(data, params, queryParams, inventoryAdapter, warehousesAdapter);
    const fixture = TestBed.createComponent(InventoryBalances);
    fixture.detectChanges();

    expect(north.observed).toBe(true);
    fixture.componentInstance['adjustmentRow'].set(
      balancePage('north-product', 'warehouse-north', 9, 1, 8).rows[0]!,
    );
    params.next(convertToParamMap({ id: 'warehouse-south' }));
    expect(north.observed).toBe(false);
    expect(fixture.componentInstance['adjustmentRow']()).toBeNull();
    expect(inventoryAdapter.listWarehouse).toHaveBeenLastCalledWith('warehouse-south', {
      page: 0,
      size: 20,
    });

    north.next(balancePage('north-product', 'warehouse-north', 9, 1, 8));
    north.complete();
    south.next(balancePage('south-product', 'warehouse-south', 3, 1, 2));
    south.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('SKU-south-product');
    expect(fixture.nativeElement.textContent).not.toContain('SKU-north-product');
    expect(fixture.nativeElement.textContent).toContain('warehouse-south');
  });

  it('exposes adjustment only to managers and reconciles the selected row from the API response', async () => {
    const data = new BehaviorSubject<Data>({ inventoryScope: 'warehouse' });
    const params = new BehaviorSubject(convertToParamMap({ id: 'warehouse-north' }));
    const queryParams = new BehaviorSubject(convertToParamMap({}));
    const page = balancePage('product-a', 'warehouse-north', 8, 3, 5);
    const inventoryAdapter = {
      listMain: vi.fn(),
      listWarehouse: vi.fn(() => of(page)),
    };
    const warehousesAdapter = {
      list: vi.fn(() => of({ content: [], first: true, last: true })),
      get: vi.fn(() => of({ id: 'warehouse-north', code: 'NORTH', name: 'Norte', active: true })),
    };
    await configureComponent(data, params, queryParams, inventoryAdapter, warehousesAdapter, true);
    const fixture = TestBed.createComponent(InventoryBalances);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component['openAdjustment'](page.rows[0]!);
    expect(component['adjustmentRow']()?.balance.quantity).toBe(8);

    component['reconcileAdjustment']({
      productId: 'product-a',
      warehouseId: 'warehouse-north',
      quantity: 6,
      reservedQuantity: 3,
      availableQuantity: 3,
    });

    expect(component['rows']()[0]?.balance).toMatchObject({
      quantity: 6,
      reservedQuantity: 3,
      availableQuantity: 3,
    });
    expect(component['adjustmentRow']()).toBeNull();
    expect(component['adjustmentFeedback']()).toContain('saldo confirmado por Inventory API');
  });
});

async function configureComponent(
  data: BehaviorSubject<Data>,
  params: BehaviorSubject<ParamMap>,
  queryParams: BehaviorSubject<ParamMap>,
  inventoryAdapter: unknown,
  warehousesAdapter: unknown,
  canManage = false,
): Promise<void> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [InventoryBalances],
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
      { provide: InventoryApiAdapter, useValue: inventoryAdapter },
      { provide: WarehousesApiAdapter, useValue: warehousesAdapter },
      { provide: SessionService, useValue: { hasAnyRole: () => canManage } },
    ],
  }).compileComponents();
  vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
}

function balancePage(
  productId: string,
  warehouseId: string,
  quantity: number,
  reservedQuantity: number,
  availableQuantity: number,
): InventoryBalancePage {
  const balance = {
    productId,
    warehouseId,
    quantity,
    reservedQuantity,
    availableQuantity,
  };
  return {
    response: {
      content: [balance],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    },
    rows: [
      {
        balance,
        product: {
          productId,
          warehouseId,
          sku: `SKU-${productId}`,
          name: `Producto ${productId}`,
        },
      },
    ],
  };
}
