import { registerLocaleData } from '@angular/common';
import localeEsMx from '@angular/common/locales/es-MX';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, Subject } from 'rxjs';

import { SuppliersApiAdapter } from '../../core/api/suppliers-api.adapter';
import { SupplierDetail } from './supplier-detail';

describe('SupplierDetail', () => {
  let fixture: ComponentFixture<SupplierDetail>;
  let deactivateResult: Subject<void>;
  let dialogClosed: Subject<boolean>;
  let adapter: { get: ReturnType<typeof vi.fn>; deactivate: ReturnType<typeof vi.fn> };
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeAll(() => registerLocaleData(localeEsMx));

  beforeEach(async () => {
    deactivateResult = new Subject();
    dialogClosed = new Subject();
    adapter = {
      get: vi.fn(() =>
        of({
          id: 'supplier-1',
          code: 'SUP-MX',
          legalName: 'Proveedor Visible',
          commercialName: 'Comercial',
          fiscalIdentifier: 'RFC010101AA1',
          email: 'compras@example.test',
          phone: '+52 55 0000 0000',
          active: true,
          createdAt: '2026-08-20T12:00:00Z',
          updatedAt: '2026-08-20T12:30:00Z',
        }),
      ),
      deactivate: vi.fn(() => deactivateResult),
    };
    dialogOpen = vi.fn(() => ({ afterClosed: () => dialogClosed }));
    await TestBed.configureTestingModule({
      imports: [SupplierDetail],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'supplier-1' }),
              queryParamMap: convertToParamMap({ page: '2', code: 'SUP', result: 'created' }),
            },
          },
        },
        { provide: SuppliersApiAdapter, useValue: adapter },
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: LOCALE_ID, useValue: 'es-MX' },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SupplierDetail);
    fixture.detectChanges();
  });

  it('shows normalized API data, locale dates and preserved list filters', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('SUP-MX');
    expect(element.textContent).toContain('RFC010101AA1');
    expect(element.textContent).toContain('compras@example.test');
    expect(element.textContent).toContain('20 ago 2026');
    expect(element.textContent).toContain('valores normalizados por el servidor');
    const backLink = element.querySelector<HTMLAnchorElement>('a[href^="/suppliers?"]');
    expect(backLink?.getAttribute('href')).toContain('page=2');
    expect(backLink?.getAttribute('href')).toContain('code=SUP');
  });

  it('prevents double deactivation and navigates only after the contracted 204 completes', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    fixture.componentInstance['requestDeactivate']();
    fixture.componentInstance['requestDeactivate']();
    expect(dialogOpen).toHaveBeenCalledOnce();
    dialogClosed.next(true);
    dialogClosed.complete();
    expect(adapter.deactivate).toHaveBeenCalledOnce();
    expect(navigate).not.toHaveBeenCalled();

    deactivateResult.next();
    deactivateResult.complete();
    expect(navigate).toHaveBeenCalledWith(['/suppliers'], {
      queryParams: { page: 2, code: 'SUP', result: 'deactivated' },
    });
  });
});
