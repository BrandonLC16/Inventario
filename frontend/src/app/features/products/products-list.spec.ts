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
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';

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

  beforeEach(async () => {
    queryParams = new BehaviorSubject(convertToParamMap({ sku: 'ABC', page: '1' }));
    listResult = new Subject();
    deleteResult = new Subject();
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
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(true) }) } },
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
    expect(fixture.nativeElement.textContent).toContain('Ver');
  });

  it('confirms deletion and prevents a duplicate request while it is pending', () => {
    const product = { id: 'product-1', sku: 'SKU-1', name: 'Demo' };

    fixture.componentInstance['requestDelete'](product);
    fixture.componentInstance['requestDelete'](product);

    expect(adapter.delete).toHaveBeenCalledOnce();
    expect(fixture.componentInstance['deletingIds']().has('product-1')).toBe(true);
  });
});
