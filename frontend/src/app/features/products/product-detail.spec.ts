import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ProductsApiAdapter } from '../../core/api/products-api.adapter';
import { SessionService } from '../../core/session/session.service';
import { ProductDetail } from './product-detail';

describe('ProductDetail', () => {
  let fixture: ComponentFixture<ProductDetail>;

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
              }),
            delete: vi.fn(),
          },
        },
        { provide: SessionService, useValue: { hasAnyRole: () => false } },
        { provide: MatDialog, useValue: { open: vi.fn() } },
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
});
