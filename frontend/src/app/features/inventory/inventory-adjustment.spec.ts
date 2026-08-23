import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';

import { InventoryResponse } from '../../core/api/generated/model/inventory-response';
import { InventoryApiAdapter, InventoryBalanceRow } from '../../core/api/inventory-api.adapter';
import { InventoryAdjustment } from './inventory-adjustment';

describe('InventoryAdjustment', () => {
  it('translates an output to one signed request and emits only the API response', async () => {
    const response = new Subject<InventoryResponse>();
    const fixture = await createFixture({ adjustStock: vi.fn(() => response) });
    const component = fixture.componentInstance;
    const emitted: InventoryResponse[] = [];
    component.adjusted.subscribe((balance) => emitted.push(balance));
    component['form'].setValue({ direction: 'out', quantity: 2, reference: ' MERMA-01 ' });

    component['requestConfirmation']();
    const dialog = TestBed.inject(MatDialog) as unknown as DialogStub;
    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(dialog.open.mock.calls[0]?.[1]?.data.message).toContain('Ajuste: -2');
    expect(dialog.open.mock.calls[0]?.[1]?.data.message).toContain('Resultado previsto: 6 físicas');
    expect(emitted).toEqual([]);

    dialog.closed.next(true);
    dialog.closed.next(true);
    component['requestConfirmation']();
    const adapter = TestBed.inject(InventoryApiAdapter) as unknown as AdapterStub;
    expect(adapter.adjustStock).toHaveBeenCalledTimes(1);
    expect(adapter.adjustStock).toHaveBeenCalledWith('warehouse-north', 'product-a', {
      quantityDelta: -2,
      reference: 'MERMA-01',
    });
    expect(emitted).toEqual([]);

    const reconciled = balance(6, 3, 3);
    response.next(reconciled);
    response.complete();
    expect(emitted).toEqual([reconciled]);
  });

  it('cancels confirmation without sending and rejects zero, oversized reference and known shortage', async () => {
    const fixture = await createFixture();
    const component = fixture.componentInstance;
    const dialog = TestBed.inject(MatDialog) as unknown as DialogStub;
    const adapter = TestBed.inject(InventoryApiAdapter) as unknown as AdapterStub;

    component['requestConfirmation']();
    dialog.closed.next(false);
    expect(adapter.adjustStock).not.toHaveBeenCalled();

    component['form'].setValue({ direction: 'in', quantity: 0, reference: '' });
    component['requestConfirmation']();
    component['form'].setValue({ direction: 'in', quantity: 1, reference: 'x'.repeat(129) });
    component['requestConfirmation']();
    component['form'].setValue({ direction: 'out', quantity: 6, reference: '' });
    component['requestConfirmation']();

    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(component['localError']()).toContain('stock puede estar reservado');
  });

  it.each([
    { status: 400, code: 'INVALID_REQUEST', expected: 'servidor rechazó la salida' },
    { status: 401, code: 'AUTHENTICATION_REQUIRED', expected: 'sesión ya no está disponible' },
    { status: 403, code: 'ACCESS_DENIED', expected: 'No tienes permiso' },
  ])('keeps context and never retries an HTTP $status rejection', async (scenario) => {
    const adapter = {
      adjustStock: vi.fn(() =>
        throwError(
          () =>
            new HttpErrorResponse({
              status: scenario.status,
              error: { code: scenario.code, correlationId: `adjust-${scenario.status}` },
            }),
        ),
      ),
    };
    const fixture = await createFixture(adapter);
    fixture.componentInstance['form'].setValue({
      direction: 'out',
      quantity: 2,
      reference: 'CONTEXT-KEPT',
    });
    fixture.componentInstance['requestConfirmation']();
    (TestBed.inject(MatDialog) as unknown as DialogStub).closed.next(true);
    fixture.detectChanges();

    expect(adapter.adjustStock).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance['form'].getRawValue()).toEqual({
      direction: 'out',
      quantity: 2,
      reference: 'CONTEXT-KEPT',
    });
    expect(fixture.nativeElement.textContent).toContain(scenario.expected);
  });

  it('marks a network failure as uncertain and does not offer mutation retry', async () => {
    const adapter = {
      adjustStock: vi.fn(() =>
        throwError(() => new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' })),
      ),
    };
    const fixture = await createFixture(adapter);
    fixture.componentInstance['requestConfirmation']();
    (TestBed.inject(MatDialog) as unknown as DialogStub).closed.next(true);
    fixture.detectChanges();

    expect(adapter.adjustStock).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('no es seguro asumir el resultado');
    expect(fixture.nativeElement.textContent).not.toContain('Reintentar');
  });

  it('honors Retry-After and blocks another confirmation after 429', async () => {
    const adapter = {
      adjustStock: vi.fn(() =>
        throwError(
          () =>
            new HttpErrorResponse({
              status: 429,
              headers: new HttpHeaders({ 'Retry-After': '30' }),
              error: { code: 'RATE_LIMIT_EXCEEDED' },
            }),
        ),
      ),
    };
    const fixture = await createFixture(adapter);
    fixture.componentInstance['requestConfirmation']();
    const dialog = TestBed.inject(MatDialog) as unknown as DialogStub;
    dialog.closed.next(true);
    fixture.detectChanges();

    expect(fixture.componentInstance['retryAfter'].blocked()).toBe(true);
    fixture.componentInstance['requestConfirmation']();
    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(adapter.adjustStock).toHaveBeenCalledTimes(1);
  });
});

interface AdapterStub {
  readonly adjustStock: ReturnType<typeof vi.fn>;
}

interface DialogStub {
  readonly closed: Subject<boolean | undefined>;
  readonly open: ReturnType<typeof vi.fn>;
}

async function createFixture(
  adapter: AdapterStub = { adjustStock: vi.fn(() => of(balance(9, 3, 6))) },
): Promise<ComponentFixture<InventoryAdjustment>> {
  TestBed.resetTestingModule();
  const closed = new Subject<boolean | undefined>();
  await TestBed.configureTestingModule({
    imports: [InventoryAdjustment],
    providers: [
      { provide: InventoryApiAdapter, useValue: adapter },
      {
        provide: MatDialog,
        useValue: {
          closed,
          open: vi.fn(() => ({ afterClosed: () => closed, close: vi.fn() })),
        },
      },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(InventoryAdjustment);
  fixture.componentRef.setInput('row', row());
  fixture.componentRef.setInput('warehouseId', 'warehouse-north');
  fixture.componentRef.setInput('locationLabel', 'NORTH');
  fixture.detectChanges();
  return fixture;
}

function row(): InventoryBalanceRow {
  return {
    product: {
      warehouseId: 'warehouse-north',
      productId: 'product-a',
      sku: 'SKU-A',
      name: 'Producto A',
      minimumStock: 0,
      active: true,
    },
    balance: balance(8, 3, 5),
  };
}

function balance(
  quantity: number,
  reservedQuantity: number,
  availableQuantity: number,
): InventoryResponse {
  return {
    warehouseId: 'warehouse-north',
    productId: 'product-a',
    quantity,
    reservedQuantity,
    availableQuantity,
    updatedAt: '2026-08-23T12:00:00Z',
  };
}
