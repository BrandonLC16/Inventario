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

import { PageResponseWarehouseResponse } from '../../core/api/generated/model/page-response-warehouse-response';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { SessionService } from '../../core/session/session.service';
import { WarehousesList } from './warehouses-list';

describe('WarehousesList', () => {
  let fixture: ComponentFixture<WarehousesList>;
  let queryParams: BehaviorSubject<ParamMap>;
  let listResult: Subject<PageResponseWarehouseResponse>;
  let deactivateResult: Subject<unknown>;
  let adapter: {
    list: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
  };
  let canManage: ReturnType<typeof signal<boolean>>;

  beforeEach(async () => {
    queryParams = new BehaviorSubject(convertToParamMap({ page: '1', search: 'ignored' }));
    listResult = new Subject();
    deactivateResult = new Subject();
    adapter = {
      list: vi.fn(() => listResult),
      deactivate: vi.fn(() => deactivateResult),
    };
    canManage = signal(true);

    await TestBed.configureTestingModule({
      imports: [WarehousesList],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParams.asObservable(),
            snapshot: { queryParamMap: queryParams.value },
          },
        },
        { provide: WarehousesApiAdapter, useValue: adapter },
        { provide: SessionService, useValue: { hasAnyRole: () => canManage() } },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(true) }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WarehousesList);
    fixture.detectChanges();
  });

  it('loads only a remote page and renders loading, empty and recoverable error states', () => {
    expect(adapter.list).toHaveBeenCalledWith({ page: 1, size: 20 });
    expect(fixture.nativeElement.querySelector('input')).toBeNull();

    listResult.next({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay almacenes para mostrar');

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

  it('writes only page and size to router navigation', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture.componentInstance['goToPage'](2);

    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: { page: 2 },
    });
  });

  it('cancels an obsolete page request when query parameters change', () => {
    expect(listResult.observed).toBe(true);
    const secondResult = new Subject<PageResponseWarehouseResponse>();
    adapter.list.mockReturnValue(secondResult);

    queryParams.next(convertToParamMap({ page: '2', size: '25', search: 'still-ignored' }));

    expect(listResult.observed).toBe(false);
    expect(adapter.list).toHaveBeenLastCalledWith({ page: 2, size: 25 });
  });

  it('shows management actions only when the shared role policy allows them', () => {
    listResult.next({
      content: [{ id: 'warehouse-1', code: 'MAIN', name: 'Principal', active: true }],
      first: true,
      last: true,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nuevo almacén');
    expect(fixture.nativeElement.textContent).toContain('Editar');
    expect(fixture.nativeElement.textContent).toContain('Desactivar');

    canManage.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Nuevo almacén');
    expect(fixture.nativeElement.textContent).not.toContain('Editar');
    expect(fixture.nativeElement.textContent).not.toContain('Desactivar');
    expect(fixture.nativeElement.textContent).toContain('Ver');
  });

  it('confirms deactivation and prevents a duplicate request while pending', () => {
    const warehouse = { id: 'warehouse-1', code: 'MAIN', name: 'Principal', active: true };

    fixture.componentInstance['requestDeactivate'](warehouse);
    fixture.componentInstance['requestDeactivate'](warehouse);

    expect(adapter.deactivate).toHaveBeenCalledOnce();
    expect(fixture.componentInstance['deactivatingIds']().has('warehouse-1')).toBe(true);
  });

  it('renders a stable 409 when stock, reservations or open documents block deactivation', () => {
    adapter.deactivate.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              code: 'CONFLICT',
              message: 'Internal stock detail must stay hidden',
              correlationId: 'warehouse-conflict',
            },
          }),
      ),
    );

    fixture.componentInstance['requestDeactivate']({
      id: 'warehouse-1',
      code: 'MAIN',
      name: 'Principal',
      active: true,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('La operación entra en conflicto');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[readonly]')
        ?.value,
    ).toBe('warehouse-conflict');
    expect(fixture.nativeElement.textContent).not.toContain('Internal stock detail');
  });
});
