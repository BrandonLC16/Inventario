import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { signal } from '@angular/core';

import { App } from '../../app';
import { routes } from '../../app.routes';
import { CurrentUserResponse } from '../../core/api/generated/model/current-user-response';
import { AppRole } from '../../core/navigation/app-navigation';
import { SessionService } from '../../core/session/session.service';

class SessionStub {
  private readonly roleState = signal<readonly AppRole[]>(['ADMIN']);
  private readonly userState = signal<CurrentUserResponse | null>({
    username: 'alicia',
    roles: new Set(['ADMIN']),
  });

  readonly roles = this.roleState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = signal(true).asReadonly();
  readonly logout = vi.fn(() => of(undefined));

  setRole(role: AppRole): void {
    this.roleState.set([role]);
    this.userState.set({ username: role.toLowerCase(), roles: new Set([role]) });
  }
}

describe('AppShell', () => {
  let fixture: ComponentFixture<App>;
  let router: Router;
  let session: SessionStub;

  beforeEach(async () => {
    session = new SessionStub();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), { provide: SessionService, useValue: session }],
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    router = TestBed.inject(Router);
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

  it('redirects unauthorized navigation to forbidden', async () => {
    session.setRole('SALES');
    const element = await navigate('/admin/users');

    expect(router.url).toBe('/forbidden');
    expect(element.querySelector('h1')?.textContent).toContain('Acceso no disponible');
    expect(element.querySelector('[data-error-source="routing"]')).not.toBeNull();
    expect(element.querySelector('input[readonly]')).toBeNull();
  });

  it('allows navigation when any assigned role has access', async () => {
    session.setRole('INVENTORY_MANAGER');
    const element = await navigate('/inventory-transfers');

    expect(router.url).toBe('/inventory-transfers');
    expect(element.querySelector('h1')?.textContent).toContain('Transferencias');
  });

  it('renders a dedicated page for unknown authenticated routes', async () => {
    const element = await navigate('/route-that-does-not-exist');

    expect(element.querySelector('h1')?.textContent).toContain('Página no encontrada');
    expect(element.querySelector('[data-error-source="routing"]')).not.toBeNull();
    expect(element.textContent).toContain('ruta disponible en el cliente');
    expect(element.querySelector('input[readonly]')).toBeNull();
  });

  it('closes the mobile menu with Escape and restores focus', async () => {
    const element = await navigate('/dashboard');
    const button = element.querySelector<HTMLButtonElement>('.menu-button');

    button?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

    expect(button?.getAttribute('aria-expanded')).toBe('true');
    expect((document.activeElement as HTMLElement | null)?.dataset['navId']).toBe('dashboard');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(button?.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(button);
  });

  it('always returns to login after logout', async () => {
    const element = await navigate('/dashboard');
    element.querySelector<HTMLButtonElement>('.session-summary button')?.click();
    await fixture.whenStable();

    expect(session.logout).toHaveBeenCalledOnce();
    expect(router.url).toBe('/login');
  });
});
