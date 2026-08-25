import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ProductsApiAdapter } from '../../core/api/products-api.adapter';
import { SessionService } from '../../core/session/session.service';
import { ProductDetail } from './product-detail';

describe('ProductDetail', () => {
  let fixture: ComponentFixture<ProductDetail>;

  beforeAll(() => registerLocaleData(localeEsMx));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'product-1' }),
              queryParamMap: convertToParamMap({ page: '2', sku: 'ABC' }),
            },
          },
        },
        {
          provide: ProductsApiAdapter,
          useValue: {
            get: () =>
              of({
                id: 'product-1',
                sku: 'SKU-1',
                name: 'Producto visible',
                description: 'Detalle',
                price: 10,
                active: true,
                createdAt: '2026-08-20T12:00:00Z',
                updatedAt: '2026-08-20T12:30:00Z',
              }),
            delete: vi.fn(),
          },
        },
        { provide: SessionService, useValue: { hasAnyRole: () => false } },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();
  });

  it('keeps list filters in the back link and hides mutations from SALES', () => {
    const element = fixture.nativeElement as HTMLElement;
    const backLink = element.querySelector<HTMLAnchorElement>('a[href^="/products?"]');

    expect(element.textContent).toContain('Producto visible');
    expect(backLink?.getAttribute('href')).toContain('page=2');
    expect(backLink?.getAttribute('href')).toContain('sku=ABC');
    expect(element.textContent).not.toContain('Editar producto');
    expect(element.textContent).not.toContain('Dar de baja');
  });

  it('renders API dates with the configured Spanish locale', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('20 ago 2026');
    expect(text).not.toContain('Aug 20, 2026');
  });
});
