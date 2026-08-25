import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { SupplierRequest } from '../../core/api/generated/model/supplier-request';
import { SupplierResponse } from '../../core/api/generated/model/supplier-response';
import { SuppliersApiAdapter } from '../../core/api/suppliers-api.adapter';
import { SupplierForm } from './supplier-form';

describe('SupplierForm create', () => {
  let fixture: ComponentFixture<SupplierForm>;
  let createResult: Subject<SupplierResponse>;
  let adapter: SupplierAdapterDouble;

  beforeEach(async () => {
    createResult = new Subject();
    adapter = {
      create: vi.fn(() => createResult),
      update: vi.fn(),
      get: vi.fn(),
    };
    await configureForm('create', adapter);
    fixture = TestBed.createComponent(SupplierForm);
    fixture.detectChanges();
  });

  it('validates all generated request limits and email before sending', () => {
    fixture.componentInstance['form'].setValue({
      code: '   ',
      legalName: 'Proveedor',
      commercialName: '',
      fiscalIdentifier: '',
      email: 'correo-no-valido',
      phone: '',
      active: true,
    });

    fixture.componentInstance['submit']();
    fixture.detectChanges();

    expect(adapter.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Revisa los campos señalados');
    expect(fixture.nativeElement.textContent).toContain('Ingresa un correo válido');
  });

  it('makes normalization visible, omits empty optionals and prevents double submit', () => {
    const router = TestBed.inject(Router);
    fixture.componentInstance['form'].setValue({
      code: ' sup-mx ',
      legalName: ' Proveedor México ',
      commercialName: ' Comercial ',
      fiscalIdentifier: ' rfc010101aa1 ',
      email: ' COMPRAS@EXAMPLE.TEST ',
      phone: '   ',
      active: true,
    });

    fixture.componentInstance['submit']();
    fixture.componentInstance['submit']();

    expect(adapter.create).toHaveBeenCalledOnce();
    expect(adapter.create).toHaveBeenCalledWith({
      code: 'SUP-MX',
      legalName: 'Proveedor México',
      commercialName: 'Comercial',
      fiscalIdentifier: 'RFC010101AA1',
      email: 'compras@example.test',
      active: true,
    } satisfies SupplierRequest);
    expect(fixture.nativeElement.textContent).toContain('código y el identificador fiscal');
    expect(fixture.nativeElement.textContent).toContain('correo en');

    createResult.next({
      id: 'supplier-from-api',
      code: 'SUP-MX',
      legalName: 'Proveedor México',
      email: 'compras@example.test',
      active: true,
    });
    expect(router.navigate).toHaveBeenCalledWith(['/suppliers', 'supplier-from-api'], {
      queryParams: { page: 2, code: 'SUP', result: 'created' },
    });
  });

  it('respects Retry-After and blocks another submission after 429', () => {
    adapter.create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            headers: new HttpHeaders({ 'Retry-After': '5' }),
            error: { code: 'RATE_LIMIT_EXCEEDED', message: 'variable backend detail' },
          }),
      ),
    );
    fixture.componentInstance['form'].setValue(validFormValue());

    fixture.componentInstance['submit']();
    fixture.componentInstance['submit']();
    fixture.detectChanges();

    expect(adapter.create).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Espera antes de volver a intentarlo');
    expect(fixture.nativeElement.textContent).toContain('Disponible en');
    expect(fixture.nativeElement.textContent).not.toContain('variable backend detail');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
        'button[type="submit"]',
      )?.disabled,
    ).toBe(true);
  });
});

describe.each(['code', 'fiscalIdentifier', 'email'])('SupplierForm %s uniqueness', (field) => {
  it('presents each 409 through the stable shared contract without parsing backend text', async () => {
    const adapter: SupplierAdapterDouble = {
      create: vi.fn(() =>
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: {
                code: 'CONFLICT',
                message: `internal duplicate ${field} constraint with token=secret`,
                correlationId: `duplicate-${field}`,
              },
            }),
        ),
      ),
      update: vi.fn(),
      get: vi.fn(),
    };
    await configureForm('create', adapter);
    const fixture = TestBed.createComponent(SupplierForm);
    fixture.detectChanges();
    fixture.componentInstance['form'].setValue(validFormValue());

    fixture.componentInstance['submit']();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('La operación entra en conflicto');
    expect(text).toContain('código, el identificador fiscal y el correo deben ser únicos');
    expect(text).not.toContain(`internal duplicate ${field}`);
    expect(text).not.toContain('token=secret');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[readonly]')
        ?.value,
    ).toBe(`duplicate-${field}`);
  });
});

describe('SupplierForm edit', () => {
  it('confirms active-to-inactive once and reconciles the generated API response', async () => {
    const updateResult = new Subject<SupplierResponse>();
    const dialogClosed = new Subject<boolean>();
    const dialogOpen = vi.fn(() => ({ afterClosed: () => dialogClosed }));
    const adapter: SupplierAdapterDouble = {
      create: vi.fn(),
      update: vi.fn(() => updateResult),
      get: vi.fn(() =>
        of({ id: 'supplier-1', code: 'SUP-1', legalName: 'Original', active: true }),
      ),
    };
    await configureForm('edit', adapter, dialogOpen);
    const fixture = TestBed.createComponent(SupplierForm);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    fixture.componentInstance['form'].patchValue({ active: false });

    fixture.componentInstance['submit']();
    fixture.componentInstance['submit']();
    expect(dialogOpen).toHaveBeenCalledOnce();
    expect(adapter.update).not.toHaveBeenCalled();
    dialogClosed.next(true);
    dialogClosed.complete();

    expect(adapter.update).toHaveBeenCalledOnce();
    expect(adapter.update).toHaveBeenCalledWith(
      'supplier-1',
      expect.objectContaining({ code: 'SUP-1', active: false }),
    );
    updateResult.next({
      id: 'supplier-from-api',
      code: 'SUP-1',
      legalName: 'Servidor',
      active: false,
    });
    expect(router.navigate).toHaveBeenCalledWith(['/suppliers', 'supplier-from-api'], {
      queryParams: { page: 2, code: 'SUP', result: 'updated' },
    });
  });

  it('renders 404 during load as a recoverable state', async () => {
    const adapter: SupplierAdapterDouble = {
      create: vi.fn(),
      update: vi.fn(),
      get: vi
        .fn()
        .mockReturnValueOnce(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 404,
                error: { code: 'RESOURCE_NOT_FOUND', correlationId: 'supplier-load-404' },
              }),
          ),
        )
        .mockReturnValueOnce(
          of({ id: 'supplier-1', code: 'SUP-1', legalName: 'Recuperado', active: true }),
        ),
    };
    await configureForm('edit', adapter);
    const fixture = TestBed.createComponent(SupplierForm);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se encontró el recurso');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
    fixture.componentInstance['retryLoad']();
    fixture.detectChanges();

    expect(adapter.get).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance['form'].controls.legalName.value).toBe('Recuperado');
  });
});

interface SupplierAdapterDouble {
  create: ReturnType<typeof vi.fn>;
  update: ReturnType<typeof vi.fn>;
  get: ReturnType<typeof vi.fn>;
}

async function configureForm(
  mode: 'create' | 'edit',
  adapter: SupplierAdapterDouble,
  dialogOpen: ReturnType<typeof vi.fn> = vi.fn(() => ({ afterClosed: () => of(false) })),
): Promise<void> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [SupplierForm],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            data: { formMode: mode },
            paramMap: convertToParamMap(mode === 'edit' ? { id: 'supplier-1' } : {}),
            queryParamMap: convertToParamMap({ page: '2', code: 'SUP' }),
          },
        },
      },
      { provide: SuppliersApiAdapter, useValue: adapter },
      { provide: MatDialog, useValue: { open: dialogOpen } },
    ],
  }).compileComponents();
  vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
}

function validFormValue() {
  return {
    code: 'SUP-1',
    legalName: 'Proveedor Uno',
    commercialName: '',
    fiscalIdentifier: 'RFC010101AA1',
    email: 'compras@example.test',
    phone: '',
    active: true,
  };
}
