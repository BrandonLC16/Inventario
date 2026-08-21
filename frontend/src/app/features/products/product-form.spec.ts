import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { ProductRequest } from '../../core/api/generated/model/product-request';
import { ProductResponse } from '../../core/api/generated/model/product-response';
import { ProductsApiAdapter } from '../../core/api/products-api.adapter';
import { ProductForm } from './product-form';

describe('ProductForm create', () => {
  let fixture: ComponentFixture<ProductForm>;
  let createResult: Subject<ProductResponse>;
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
    fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
  });

  it('validates the generated request constraints before submitting', () => {
    const component = fixture.componentInstance;
    component['form'].setValue({
      sku: '   ',
      name: 'Demo',
      description: '',
      price: 1.999,
      active: true,
      minimumStock: -1,
    });

    component['submit']();
    fixture.detectChanges();

    expect(adapter.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Revisa los campos señalados',
    );
  });

  it('sends minimumStock only on creation and prevents double submit', () => {
    const component = fixture.componentInstance;
    component['form'].setValue({
      sku: ' sku-main ',
      name: ' Producto ',
      description: ' Demo ',
      price: 12.5,
      active: true,
      minimumStock: 7,
    });

    component['submit']();
    component['submit']();

    expect(adapter.create).toHaveBeenCalledOnce();
    expect(adapter.create).toHaveBeenCalledWith({
      sku: 'sku-main',
      name: 'Producto',
      description: 'Demo',
      price: 12.5,
      active: true,
      minimumStock: 7,
    } satisfies ProductRequest);
  });

  it('maps a duplicate SKU conflict without exposing variable server text', () => {
    adapter.create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'CONFLICT', message: 'database constraint and token=secret' },
          }),
      ),
    );
    const component = fixture.componentInstance;
    component['form'].setValue({
      sku: 'DUPLICATE',
      name: 'Demo',
      description: '',
      price: 1,
      active: true,
      minimumStock: 0,
    });

    component['submit']();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('La operación entra en conflicto');
    expect(fixture.nativeElement.textContent).not.toContain('database constraint');
    expect(fixture.nativeElement.textContent).not.toContain('token=secret');
  });
});

describe('ProductForm edit', () => {
  it('loads generated product data and never includes minimumStock in update', async () => {
    const updateResult = new Subject<ProductResponse>();
    const adapter = {
      create: vi.fn(),
      update: vi.fn((id: string, request: ProductRequest) => {
        void id;
        void request;
        return updateResult;
      }),
      get: vi.fn(() =>
        of({ id: 'product-1', sku: 'SKU-1', name: 'Original', price: 8, active: true }),
      ),
    };
    await configureForm('edit', adapter);
    const fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component['form'].patchValue({ name: 'Editado', minimumStock: 99 });

    component['submit']();

    expect(adapter.get).toHaveBeenCalledWith('product-1');
    expect(adapter.update).toHaveBeenCalledOnce();
    const request = adapter.update.mock.calls[0]?.[1] as ProductRequest;
    expect(request.name).toBe('Editado');
    expect(request).not.toHaveProperty('minimumStock');
    expect(fixture.nativeElement.textContent).not.toContain('Stock mínimo inicial');
  });

  it('renders an incomplete load as a recoverable API state', async () => {
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
                error: { code: 'RESOURCE_NOT_FOUND', correlationId: 'product-load-404' },
              }),
          ),
        )
        .mockReturnValueOnce(
          of({ id: 'product-1', sku: 'SKU-1', name: 'Recuperado', price: 8, active: true }),
        ),
    };
    await configureForm('edit', adapter);
    const fixture = TestBed.createComponent(ProductForm);
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
): Promise<void> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [ProductForm],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            data: { formMode: mode },
            paramMap: convertToParamMap(mode === 'edit' ? { id: 'product-1' } : {}),
            queryParamMap: convertToParamMap({ page: '2', sku: 'ABC' }),
          },
        },
      },
      { provide: ProductsApiAdapter, useValue: adapter },
    ],
  }).compileComponents();
  vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
}
