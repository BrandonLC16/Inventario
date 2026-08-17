import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { App } from '../../app';
import { routes } from '../../app.routes';
import { AppRole } from '../../core/navigation/app-navigation';
import { DemoSessionService } from '../../core/session/demo-session.service';

describe('AppShell', () => {
  let fixture: ComponentFixture<App>;
  let router: Router;
  let session: DemoSessionService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes)],
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    router = TestBed.inject(Router);
    session = TestBed.inject(DemoSessionService);
  });

  async function navigate(url: string): Promise<HTMLElement> {
    await router.navigateByUrl(url);
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function navigationIds(element: HTMLElement): string[] {
    return Array.from(element.querySelectorAll<HTMLElement>('[data-nav-id]')).map(
      (link) => link.dataset['navId'] ?? '',
    );
  }

  it.each([
    [
      'ADMIN',
      [
        'dashboard',
        'products',
        'warehouses',
        'inventory',
        'suppliers',
        'purchases',
        'transfers',
        'inventory-counts',
        'customers',
        'orders',
        'users',
        'profile',
      ],
    ],
    [
      'INVENTORY_MANAGER',
      [
        'dashboard',
        'products',
        'warehouses',
        'inventory',
        'suppliers',
        'purchases',
        'transfers',
        'inventory-counts',
        'profile',
      ],
    ],
    [
      'SALES',
      ['dashboard', 'products', 'warehouses', 'inventory', 'customers', 'orders', 'profile'],
    ],
  ] satisfies readonly (readonly [AppRole, readonly string[]])[])(
    'renders the centralized navigation policy for %s',
    async (role, expected) => {
      session.setRole(role);
      const element = await navigate('/dashboard');

      expect(navigationIds(element)).toEqual(expected);
    },
  );

  it('builds breadcrumbs from route metadata and marks the active link', async () => {
    const element = await navigate('/purchase-orders');
    const activeLink = element.querySelector<HTMLAnchorElement>('[data-nav-id="purchases"]');

    expect(element.querySelector('.breadcrumbs [aria-current="page"]')?.textContent).toContain(
      'Compras',
    );
    expect(activeLink?.getAttribute('aria-current')).toBe('page');
  });

  it('redirects unauthorized demo navigation to forbidden', async () => {
    session.setRole('SALES');
    const element = await navigate('/admin/users');

    expect(router.url).toBe('/forbidden');
    expect(element.querySelector('h1')?.textContent).toContain('Acceso no disponible');
    expect(element.textContent).toContain('la API siempre vuelve a validar');
  });

  it('allows navigation when the demo role has access', async () => {
    session.setRole('INVENTORY_MANAGER');
    const element = await navigate('/inventory-transfers');

    expect(router.url).toBe('/inventory-transfers');
    expect(element.querySelector('h1')?.textContent).toContain('Transferencias');
  });

  it('renders a dedicated page for unknown Angular routes', async () => {
    const element = await navigate('/route-that-does-not-exist');

    expect(element.querySelector('h1')?.textContent).toContain('Página no encontrada');
    expect(element.querySelector('.breadcrumbs [aria-current="page"]')?.textContent).toContain(
      'Página no encontrada',
    );
  });

  it('closes the mobile menu with Escape and restores focus', async () => {
    const element = await navigate('/dashboard');
    const button = element.querySelector<HTMLButtonElement>('.menu-button');

    button?.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(button?.getAttribute('aria-expanded')).toBe('true');
    expect((document.activeElement as HTMLElement | null)?.dataset['navId']).toBe('dashboard');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(button?.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(button);
  });

  it('switches the in-memory demo role and returns to the dashboard', async () => {
    await navigate('/admin/users');
    const element = fixture.nativeElement as HTMLElement;
    const select = element.querySelector<HTMLSelectElement>('#demo-role');

    if (!select) {
      throw new Error('Demo role selector was not rendered');
    }

    select.value = 'SALES';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(session.role()).toBe('SALES');
    expect(router.url).toBe('/dashboard');
    expect(navigationIds(fixture.nativeElement as HTMLElement)).not.toContain('users');
  });
});
