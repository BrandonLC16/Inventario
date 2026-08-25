import { HttpErrorResponse } from '@angular/common/http';
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

import { PageResponseSupplierResponse } from '../../core/api/generated/model/page-response-supplier-response';
import { SuppliersApiAdapter } from '../../core/api/suppliers-api.adapter';
import { SuppliersList } from './suppliers-list';

describe('SuppliersList', () => {
  let fixture: ComponentFixture<SuppliersList>;
  let queryParams: BehaviorSubject<ParamMap>;
  let listResult: Subject<PageResponseSupplierResponse>;
  let deactivateResult: Subject<void>;
  let dialogClosed: Subject<boolean>;
  let adapter: {
    list: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
  };
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    queryParams = new BehaviorSubject(convertToParamMap({ code: 'SUP', page: '1' }));
    listResult = new Subject();
    deactivateResult = new Subject();
    dialogClosed = new Subject();
    adapter = {
      list: vi.fn(() => listResult),
      deactivate: vi.fn(() => deactivateResult),
    };
    dialogOpen = vi.fn(() => ({ afterClosed: () => dialogClosed }));

    await TestBed.configureTestingModule({
      imports: [SuppliersList],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParams.asObservable(),
            snapshot: { queryParamMap: queryParams.value },
          },
        },
        { provide: SuppliersApiAdapter, useValue: adapter },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SuppliersList);
    fixture.detectChanges();
  });

  it('loads a remote page from URL filters and renders empty and recoverable error states', () => {
    expect(adapter.list).toHaveBeenCalledWith({ page: 1, size: 20, code: 'SUP' });
    listResult.next({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay proveedores para mostrar');

    adapter.list.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            error: { code: 'RATE_LIMIT_EXCEEDED', message: 'variable backend detail' },
          }),
      ),
    );
    fixture.componentInstance['retry']();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Espera antes de volver a intentarlo');
    expect(fixture.nativeElement.textContent).not.toContain('variable backend detail');
  });

  it('writes all combinable filters and page zero to router navigation', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.componentInstance['filterForm'].setValue({
      code: ' sup-02 ',
      name: ' Comercial ',
      fiscalIdentifier: ' rfc ',
      active: 'false',
    });

    fixture.componentInstance['applyFilters']();

    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {
        code: 'sup-02',
        name: 'Comercial',
        fiscalIdentifier: 'rfc',
        active: false,
      },
    });
  });

  it('cancels an obsolete request when URL filters change', () => {
    expect(listResult.observed).toBe(true);
    const latestResult = new Subject<PageResponseSupplierResponse>();
    adapter.list.mockReturnValue(latestResult);

    queryParams.next(
      convertToParamMap({ page: '2', name: 'Nuevo', fiscalIdentifier: 'RFC', active: 'true' }),
    );

    expect(listResult.observed).toBe(false);
    expect(adapter.list).toHaveBeenLastCalledWith({
      page: 2,
      size: 20,
      name: 'Nuevo',
      fiscalIdentifier: 'RFC',
      active: true,
    });
  });

  it('confirms once, waits for 204 and then reloads the visible supplier state', () => {
    const supplier = {
      id: 'supplier-1',
      code: 'SUP-01',
      legalName: 'Proveedor Uno',
      active: true,
    };
    listResult.next({ content: [supplier], first: true, last: true });
    fixture.detectChanges();
    const reconciled = new Subject<PageResponseSupplierResponse>();
    adapter.list.mockReturnValue(reconciled);

    fixture.componentInstance['requestDeactivate'](supplier);
    fixture.componentInstance['requestDeactivate'](supplier);
    expect(dialogOpen).toHaveBeenCalledOnce();
    expect(dialogOpen.mock.calls[0]?.[1]?.data.message).toContain('preferencias de abastecimiento');
    expect(dialogOpen.mock.calls[0]?.[1]?.data.message).toContain('historial de compras');
    dialogClosed.next(true);
    dialogClosed.complete();

    expect(adapter.deactivate).toHaveBeenCalledOnce();
    expect(adapter.list).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Proveedor Uno');

    deactivateResult.next();
    deactivateResult.complete();
    expect(adapter.list).toHaveBeenCalledTimes(2);

    reconciled.next({ content: [{ ...supplier, active: false }], first: true, last: true });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('se desactivó correctamente');
    expect(fixture.nativeElement.textContent).toContain('Inactivo');
    expect(fixture.nativeElement.textContent).not.toContain('Procesando…');
  });

  it('does not offer deactivation again for an inactive supplier', () => {
    listResult.next({
      content: [{ id: 'supplier-1', code: 'SUP-01', legalName: 'Inactivo', active: false }],
      first: true,
      last: true,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Editar');
    expect(fixture.nativeElement.querySelector('button')?.textContent).not.toContain('Desactivar');
  });
});
