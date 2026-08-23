import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { SessionService } from '../../core/session/session.service';
import { WarehouseDetail } from './warehouse-detail';

describe('WarehouseDetail', () => {
  it('keeps pagination in the back link, identifies inactive state and hides mutations from SALES', async () => {
    const fixture = await createDetailFixture({
      get: () =>
        of({
          id: 'warehouse-1',
          code: 'NORTH',
          name: 'Almacén norte',
          description: 'Detalle',
          active: false,
        }),
      deactivate: vi.fn(),
    });
    const element = fixture.nativeElement as HTMLElement;
    const backLink = element.querySelector<HTMLAnchorElement>('a[href^="/warehouses?"]');

    expect(element.textContent).toContain('Almacén norte');
    expect(element.textContent).toContain('no admite nuevas operaciones');
    expect(backLink?.getAttribute('href')).toContain('page=2');
    expect(backLink?.getAttribute('href')).toContain('size=25');
    expect(element.textContent).not.toContain('Editar almacén');
    expect(element.textContent).not.toContain('Desactivar');
  });

  it('renders a 404 as a recoverable state with its support reference', async () => {
    const fixture = await createDetailFixture({
      get: () =>
        throwError(
          () =>
            new HttpErrorResponse({
              status: 404,
              error: { code: 'RESOURCE_NOT_FOUND', correlationId: 'warehouse-detail-404' },
            }),
        ),
      deactivate: vi.fn(),
    });

    expect(fixture.nativeElement.textContent).toContain('No se encontró el recurso');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[readonly]')
        ?.value,
    ).toBe('warehouse-detail-404');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });
});

async function createDetailFixture(adapter: {
  get: () => unknown;
  deactivate: ReturnType<typeof vi.fn>;
}): Promise<ComponentFixture<WarehouseDetail>> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [WarehouseDetail],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            paramMap: convertToParamMap({ id: 'warehouse-1' }),
            queryParamMap: convertToParamMap({ page: '2', size: '25' }),
          },
        },
      },
      { provide: WarehousesApiAdapter, useValue: adapter },
      { provide: SessionService, useValue: { hasAnyRole: () => false } },
      { provide: MatDialog, useValue: { open: vi.fn() } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(WarehouseDetail);
  fixture.detectChanges();
  return fixture;
}
