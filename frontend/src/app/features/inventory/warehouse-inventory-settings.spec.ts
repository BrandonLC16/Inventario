import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';

import { InventorySettingResponse } from '../../core/api/generated/model/inventory-setting-response';
import { PageResponseInventorySettingResponse } from '../../core/api/generated/model/page-response-inventory-setting-response';
import { InventoryApiAdapter } from '../../core/api/inventory-api.adapter';
import { ProductsApiAdapter } from '../../core/api/products-api.adapter';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { SessionService } from '../../core/session/session.service';
import { WarehouseInventorySettings } from './warehouse-inventory-settings';

describe('WarehouseInventorySettings', () => {
  it('loads zero, distinguishes global/local state and keeps SALES read-only', async () => {
    const setting = inventorySetting('warehouse-north', 'product-a', 0, false);
    const fixture = await createFixture({
      inventory: inventoryAdapter({
        listSettings: () => of(settingsPage(setting)),
        getSetting: vi.fn(() => of(setting)),
      }),
      product: { get: vi.fn(() => of({ id: 'product-a', active: false })) },
      canManage: false,
    });

    expect(fixture.nativeElement.textContent).toContain('0');
    expect(fixture.nativeElement.textContent).toContain('Inactivo aquí');
    expect(fixture.nativeElement.textContent).toContain('Dos estados independientes');

    fixture.componentInstance['openSetting'](setting);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Inactivo en catálogo');
    expect(text).toContain('Inactivo aquí');
    expect(text).toContain('Tienes acceso de consulta');
    expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();
  });

  it('validates non-negative integers, prevents double submit and applies only reconciled data', async () => {
    const setting = inventorySetting('warehouse-north', 'product-a', 2, true);
    const reconciled = new Subject<InventorySettingResponse>();
    const inventory = inventoryAdapter({
      listSettings: () => of(settingsPage(setting)),
      configureSetting: vi.fn(() => reconciled),
    });
    const fixture = await createFixture({ inventory, canManage: true });
    fixture.componentInstance['openSetting'](setting);
    fixture.detectChanges();

    const form = fixture.componentInstance['form'];
    form.controls.minimumStock.setValue(-1);
    fixture.componentInstance['submit']();
    expect(inventory.configureSetting).not.toHaveBeenCalled();
    expect(fixture.componentInstance['localError']()).toContain('Revisa el mínimo');

    form.controls.minimumStock.setValue(0);
    form.controls.active.setValue(false);
    fixture.componentInstance['submit']();
    fixture.componentInstance['submit']();
    expect(inventory.configureSetting).toHaveBeenCalledTimes(1);
    expect(inventory.configureSetting).toHaveBeenCalledWith('warehouse-north', 'product-a', {
      minimumStock: 0,
      active: false,
    });
    expect(fixture.componentInstance['rows']()[0]?.minimumStock).toBe(2);

    reconciled.next(inventorySetting('warehouse-north', 'product-a', 0, false));
    reconciled.complete();
    fixture.detectChanges();
    expect(fixture.componentInstance['rows']()[0]).toMatchObject({
      minimumStock: 0,
      active: false,
    });
    expect(fixture.nativeElement.textContent).toContain('se reconcilió con el servidor');
  });

  it('keeps form values and shows actionable support data after a deactivation conflict', async () => {
    const setting = inventorySetting('warehouse-north', 'product-a', 2, true);
    const inventory = inventoryAdapter({
      listSettings: () => of(settingsPage(setting)),
      configureSetting: vi.fn(() =>
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              headers: new HttpHeaders({ 'X-Correlation-ID': 'settings-conflict-409' }),
              error: { code: 'CONFLICT' },
            }),
        ),
      ),
    });
    const fixture = await createFixture({ inventory, canManage: true });
    fixture.componentInstance['openSetting'](setting);
    fixture.detectChanges();

    const form = fixture.componentInstance['form'];
    form.setValue({ minimumStock: 7, active: false });
    fixture.componentInstance['submit']();
    fixture.detectChanges();

    expect(form.getRawValue()).toEqual({ minimumStock: 7, active: false });
    expect(fixture.nativeElement.textContent).toContain('stock físico o las reservas');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[readonly]')
        ?.value,
    ).toBe('settings-conflict-409');
  });

  it('renders a recoverable load error and then an empty state', async () => {
    const inventory = inventoryAdapter({
      listSettings: vi
        .fn()
        .mockReturnValueOnce(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 503,
                error: { code: 'INTERNAL_ERROR', correlationId: 'settings-retry' },
              }),
          ),
        )
        .mockReturnValueOnce(of(settingsPage())),
    });
    const fixture = await createFixture({ inventory });

    expect(fixture.nativeElement.textContent).toContain('Ocurrió un error inesperado');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');

    fixture.componentInstance['retry']();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay productos configurables');
  });

  it('cancels an obsolete warehouse page and never leaks rows across locations', async () => {
    const params = new BehaviorSubject<ParamMap>(convertToParamMap({ id: 'warehouse-north' }));
    const north = new Subject<PageResponseInventorySettingResponse>();
    const south = new Subject<PageResponseInventorySettingResponse>();
    const inventory = inventoryAdapter({
      listSettings: vi.fn(
        (warehouseId: string): Observable<PageResponseInventorySettingResponse> =>
          warehouseId === 'warehouse-north' ? north : south,
      ),
    });
    const fixture = await createFixture({ inventory, params });

    expect(north.observed).toBe(true);
    params.next(convertToParamMap({ id: 'warehouse-south' }));
    expect(north.observed).toBe(false);
    north.next(settingsPage(inventorySetting('warehouse-north', 'product-north', 1, true)));
    south.next(settingsPage(inventorySetting('warehouse-south', 'product-south', 3, true)));
    south.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('SKU-product-south');
    expect(fixture.nativeElement.textContent).not.toContain('SKU-product-north');
    expect(inventory.listSettings).toHaveBeenLastCalledWith('warehouse-south', {
      page: 0,
      size: 20,
    });
  });
});

interface FixtureOptions {
  readonly inventory?: ReturnType<typeof inventoryAdapter>;
  readonly product?: { readonly get: ReturnType<typeof vi.fn> };
  readonly canManage?: boolean;
  readonly params?: BehaviorSubject<ParamMap>;
}

async function createFixture(
  options: FixtureOptions = {},
): Promise<ComponentFixture<WarehouseInventorySettings>> {
  const params =
    options.params ?? new BehaviorSubject<ParamMap>(convertToParamMap({ id: 'warehouse-north' }));
  const queryParams = new BehaviorSubject<ParamMap>(convertToParamMap({}));
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [WarehouseInventorySettings],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          paramMap: params.asObservable(),
          queryParamMap: queryParams.asObservable(),
        },
      },
      { provide: InventoryApiAdapter, useValue: options.inventory ?? inventoryAdapter() },
      {
        provide: ProductsApiAdapter,
        useValue:
          options.product ?? ({ get: vi.fn(() => of({ id: 'product-a', active: true })) } as const),
      },
      {
        provide: WarehousesApiAdapter,
        useValue: {
          get: vi.fn((warehouseId: string) =>
            of({ id: warehouseId, code: warehouseId, name: warehouseId, active: true }),
          ),
        },
      },
      {
        provide: SessionService,
        useValue: { hasAnyRole: () => options.canManage ?? false },
      },
    ],
  }).compileComponents();
  vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(WarehouseInventorySettings);
  fixture.detectChanges();
  return fixture;
}

function inventoryAdapter(
  overrides: Partial<{
    listSettings: ReturnType<typeof vi.fn> | ((warehouseId: string) => Observable<unknown>);
    getSetting: ReturnType<typeof vi.fn>;
    configureSetting: ReturnType<typeof vi.fn>;
  }> = {},
) {
  return {
    listSettings: vi.fn(() => of(settingsPage())),
    getSetting: vi.fn((_warehouseId: string, productId: string) =>
      of(inventorySetting('warehouse-north', productId, 2, true)),
    ),
    configureSetting: vi.fn(),
    ...overrides,
  };
}

function inventorySetting(
  warehouseId: string,
  productId: string,
  minimumStock: number,
  active: boolean,
): InventorySettingResponse {
  return {
    warehouseId,
    productId,
    sku: `SKU-${productId}`,
    name: `Producto ${productId}`,
    minimumStock,
    active,
  };
}

function settingsPage(
  ...content: InventorySettingResponse[]
): PageResponseInventorySettingResponse {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true,
  };
}
