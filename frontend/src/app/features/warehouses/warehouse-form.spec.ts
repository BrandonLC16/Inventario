import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { WarehouseRequest } from '../../core/api/generated/model/warehouse-request';
import { WarehouseResponse } from '../../core/api/generated/model/warehouse-response';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { WarehouseForm } from './warehouse-form';

describe('WarehouseForm create', () => {
  let fixture: ComponentFixture<WarehouseForm>;
  let createResult: Subject<WarehouseResponse>;
  let adapter: {
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    get: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    createResult = new Subject();
    adapter = {
      create: vi.fn(() => createResult),
      update: vi.fn(),
      get: vi.fn(),
    };

    await configureForm('create', adapter);
    fixture = TestBed.createComponent(WarehouseForm);
    fixture.detectChanges();
  });

  it('validates the generated request constraints before submitting', () => {
    const component = fixture.componentInstance;
    component['form'].setValue({
      code: '   ',
      name: 'N'.repeat(161),
      description: 'D'.repeat(1001),
      active: true,
    });

    component['submit']();
    fixture.detectChanges();

    expect(adapter.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Revisa los campos señalados',
    );
  });

  it('uses generated types, leaves code normalization to the server and prevents double submit', () => {
    const component = fixture.componentInstance;
    component['form'].setValue({
      code: ' north ',
      name: ' Almacén norte ',
      description: ' Centro norte ',
      active: true,
    });

    component['submit']();
    component['submit']();

    expect(adapter.create).toHaveBeenCalledOnce();
    expect(adapter.create).toHaveBeenCalledWith({
      code: 'north',
      name: 'Almacén norte',
      description: 'Centro norte',
      active: true,
    } satisfies WarehouseRequest);
  });

  it('maps a duplicate code conflict without exposing variable server text', () => {
    adapter.create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              code: 'CONFLICT',
              message: 'warehouse unique constraint and token=secret',
              correlationId: 'duplicate-warehouse-code',
            },
          }),
      ),
    );
    const component = fixture.componentInstance;
    component['form'].setValue({
      code: 'MAIN',
      name: 'Duplicado',
      description: '',
      active: true,
    });

    component['submit']();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('La operación entra en conflicto');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[readonly]')
        ?.value,
    ).toBe('duplicate-warehouse-code');
    expect(fixture.nativeElement.textContent).not.toContain('unique constraint');
    expect(fixture.nativeElement.textContent).not.toContain('token=secret');
  });
});

describe('WarehouseForm edit', () => {
  it('loads warehouse data and confirms deactivation before one update request', async () => {
    const updateResult = new Subject<WarehouseResponse>();
    const adapter = {
      create: vi.fn(),
      update: vi.fn(() => updateResult),
      get: vi.fn(() => of({ id: 'warehouse-1', code: 'MAIN', name: 'Principal', active: true })),
    };
    const dialog = { open: vi.fn(() => ({ afterClosed: () => of(true) })) };
    await configureForm('edit', adapter, dialog);
    const fixture = TestBed.createComponent(WarehouseForm);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component['form'].patchValue({ active: false });

    component['submit']();
    component['submit']();

    expect(dialog.open).toHaveBeenCalledOnce();
    expect(adapter.update).toHaveBeenCalledOnce();
    expect(adapter.update).toHaveBeenCalledWith('warehouse-1', {
      code: 'MAIN',
      name: 'Principal',
      active: false,
    });
  });

  it('renders a 404 load as a recoverable state', async () => {
    const adapter = {
      create: vi.fn(),
      update: vi.fn(),
      get: vi
        .fn()
        .mockReturnValueOnce(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 404,
                error: { code: 'RESOURCE_NOT_FOUND', correlationId: 'warehouse-load-404' },
              }),
          ),
        )
        .mockReturnValueOnce(
          of({ id: 'warehouse-1', code: 'MAIN', name: 'Recuperado', active: true }),
        ),
    };
    await configureForm('edit', adapter);
    const fixture = TestBed.createComponent(WarehouseForm);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se encontró el recurso');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
    fixture.componentInstance['retryLoad']();
    fixture.detectChanges();

    expect(adapter.get).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance['form'].controls.name.value).toBe('Recuperado');
  });
});

async function configureForm(
  mode: 'create' | 'edit',
  adapter: { create: unknown; update: unknown; get: unknown },
  dialog: { open: unknown } = { open: vi.fn() },
): Promise<void> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [WarehouseForm],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            data: { formMode: mode },
            paramMap: convertToParamMap(mode === 'edit' ? { id: 'warehouse-1' } : {}),
            queryParamMap: convertToParamMap({ page: '2', size: '25' }),
          },
        },
      },
      { provide: WarehousesApiAdapter, useValue: adapter },
      { provide: MatDialog, useValue: dialog },
    ],
  }).compileComponents();
  vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
}
