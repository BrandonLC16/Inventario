import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import { BehaviorSubject, Subject, throwError } from 'rxjs';

import { PageResponseProductResponse } from '../../core/api/generated/model/page-response-product-response';
import { ProductsApiAdapter } from '../../core/api/products-api.adapter';
import { SessionService } from '../../core/session/session.service';
import { ProductsList } from './products-list';

describe('ProductsList', () => {
  let fixture: ComponentFixture<ProductsList>;
  let queryParams: BehaviorSubject<ParamMap>;
  let listResult: Subject<PageResponseProductResponse>;
  let deleteResult: Subject<unknown>;
  let adapter: {
    list: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let canManage: ReturnType<typeof signal<boolean>>;
  let dialogClosed: Subject<boolean>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    queryParams = new BehaviorSubject(convertToParamMap({ sku: 'ABC', page: '1' }));
    listResult = new Subject();
    deleteResult = new Subject();
    dialogClosed = new Subject();
    dialogOpen = vi.fn(() => ({ afterClosed: () => dialogClosed }));
    adapter = {
      list: vi.fn(() => listResult),
      delete: vi.fn(() => deleteResult),
    };
    canManage = signal(true);

    await TestBed.configureTestingModule({
      imports: [ProductsList],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParams.asObservable(),
            snapshot: { queryParamMap: queryParams.value },
          },
        },
        { provide: ProductsApiAdapter, useValue: adapter },
        { provide: SessionService, useValue: { hasAnyRole: () => canManage() } },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductsList);
    fixture.detectChanges();
  });

  it('loads a remote page from URL filters and renders all list states', () => {
    expect(adapter.list).toHaveBeenCalledWith({ page: 1, size: 20, sku: 'ABC' });

    listResult.next({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay productos para mostrar');

    adapter.list.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            error: { code: 'RATE_LIMIT_EXCEEDED', message: 'sensitive variable text' },
          }),
      ),
    );
    fixture.componentInstance['retry']();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Espera antes de volver a intentarlo');
    expect(fixture.nativeElement.textContent).not.toContain('sensitive variable text');
  });

  it('writes combinable filters and page zero to router navigation', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance['filterForm'].setValue({
      sku: ' SKU-2 ',
      name: ' Tornillo ',
      active: 'false',
    });

    fixture.componentInstance['applyFilters']();

    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: { sku: 'SKU-2', name: 'Tornillo', active: false },
    });
  });

  it('cancels an obsolete page request when query parameters change', () => {
    expect(listResult.observed).toBe(true);
    const secondResult = new Subject<PageResponseProductResponse>();
    adapter.list.mockReturnValue(secondResult);

    queryParams.next(convertToParamMap({ page: '2', name: 'Tuerca' }));

    expect(listResult.observed).toBe(false);
    expect(adapter.list).toHaveBeenLastCalledWith({ page: 2, size: 20, name: 'Tuerca' });
  });

  it('shows management actions only when the shared role policy allows them', () => {
    listResult.next({
      content: [{ id: 'product-1', sku: 'SKU-1', name: 'Demo', price: 10, active: true }],
      first: true,
      last: true,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nuevo producto');
    expect(fixture.nativeElement.textContent).toContain('Editar');

    canManage.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Nuevo producto');
    expect(fixture.nativeElement.textContent).not.toContain('Editar');
    expect(fixture.nativeElement.textContent).not.toContain('Dar de baja');
    expect(fixture.nativeElement.textContent).toContain('Ver');
  });

  it('identifies the terminal operation and prevents double click before confirmation or 204', () => {
    const product = { id: 'product-1', sku: 'SKU-1', name: 'Demo' };

    fixture.componentInstance['requestDelete'](product);
    fixture.componentInstance['requestDelete'](product);

    expect(dialogOpen).toHaveBeenCalledOnce();
    expect(adapter.delete).not.toHaveBeenCalled();
    const dialogData = dialogOpen.mock.calls[0]?.[1]?.data as { message: string };
    expect(dialogData.message).toContain('Demo · SKU SKU-1 · ID product-1');
    expect(dialogData.message).toContain('terminal');
    expect(dialogData.message).toContain('no libera el SKU');
    expect(dialogData.message).toContain('active=false');
    expect(dialogData.message).toContain('Kardex histórico');

    dialogClosed.next(true);
    dialogClosed.complete();
    fixture.componentInstance['requestDelete'](product);

    expect(adapter.delete).toHaveBeenCalledOnce();
    expect(fixture.componentInstance['deletingIds']().has('product-1')).toBe(true);
  });

  it('keeps a suspended product visible, editable and eligible for logical deletion', () => {
    listResult.next({
      content: [
        { id: 'product-suspended', sku: 'SKU-S', name: 'Suspendido', price: 10, active: false },
      ],
      first: true,
      last: true,
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Suspendido');
    expect(text).toContain('Editar');
    expect(text).toContain('Dar de baja');
  });

  it('waits for 204 and then reloads the catalog while retaining historical traceability', () => {
    const product = { id: 'product-1', sku: 'SKU-1', name: 'Demo', active: true };
    listResult.next({ content: [product], first: true, last: true });
    fixture.detectChanges();
    const reconciled = new Subject<PageResponseProductResponse>();
    adapter.list.mockReturnValue(reconciled);

    fixture.componentInstance['requestDelete'](product);
    dialogClosed.next(true);
    dialogClosed.complete();

    expect(adapter.list).toHaveBeenCalledOnce();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Demo');

    deleteResult.next(undefined);
    deleteResult.complete();

    expect(adapter.list).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance['deletedProductId']()).toBe('product-1');

    reconciled.next({ content: [], first: true, last: true });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('baja lógica correctamente');
    expect(element.textContent).toContain('ID histórico:');
    expect(element.textContent).toContain('product-1');
    expect(element.querySelector('a[href*="inventory/kardex"]')?.getAttribute('href')).toContain(
      'productId=product-1',
    );
  });

  it.each([
    ['stock físico', 'A product with physical inventory cannot be deleted', 'stock-conflict'],
    ['reservas', 'A product with inventory reservations cannot be deleted', 'reserve-conflict'],
    [
      'documentos pendientes',
      'A product used by pending operations cannot be deleted',
      'doc-conflict',
    ],
  ])(
    'keeps the product and presents the same safe review for a %s conflict',
    (_case, backendMessage, correlationId) => {
      const product = { id: 'product-1', sku: 'SKU-1', name: 'Demo', active: true };
      listResult.next({ content: [product], first: true, last: true });
      fixture.detectChanges();

      fixture.componentInstance['requestDelete'](product);
      dialogClosed.next(true);
      dialogClosed.complete();
      deleteResult.error(
        new HttpErrorResponse({
          status: 409,
          error: { code: 'CONFLICT', message: backendMessage, correlationId },
        }),
      );
      fixture.detectChanges();

      const element = fixture.nativeElement as HTMLElement;
      expect(element.textContent).toContain('Demo');
      expect(element.textContent).toContain('El producto se conserva');
      expect(element.textContent).toContain(
        'Puede existir stock físico, una reserva o un documento pendiente',
      );
      expect(element.textContent).not.toContain(backendMessage);
      expect(element.querySelector<HTMLInputElement>('input[readonly]')?.value).toBe(correlationId);
      expect(
        element.querySelectorAll('nav[aria-label="Enlaces para revisar la baja del producto"] a'),
      ).toHaveLength(3);
      expect(adapter.list).toHaveBeenCalledOnce();
    },
  );
});
