import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
} from '@angular/router';

import { SessionService } from '../session/session.service';
import {
  ALLOWED_ROLES_DATA_KEY,
  APP_SECTION_DATA_KEY,
  APP_SECTIONS,
  AppRole,
  INVENTORY_MANAGEMENT_ROLES,
} from './app-navigation';
import { allowedRolesGuard, authenticatedGuard, roleGuard } from './session.guards';

describe('session guards', () => {
  const authenticated = signal(false);
  const roles = signal<readonly AppRole[]>([]);
  const session = {
    isAuthenticated: authenticated.asReadonly(),
    roles: roles.asReadonly(),
    hasAnyRole: (allowedRoles: readonly AppRole[]) =>
      roles().some((role) => allowedRoles.includes(role)),
  };

  beforeEach(() => {
    authenticated.set(false);
    roles.set([]);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SessionService, useValue: session }],
    });
  });

  it('sends a visitor to login and preserves the internal return URL', () => {
    const result = TestBed.runInInjectionContext(() =>
      authenticatedGuard({} as ActivatedRouteSnapshot, { url: '/orders' } as RouterStateSnapshot),
    );

    expect(TestBed.inject(Router).serializeUrl(result as never)).toBe('/login?returnUrl=%2Forders');
  });

  it('allows an authenticated user through the session guard', () => {
    authenticated.set(true);

    expect(
      TestBed.runInInjectionContext(() =>
        authenticatedGuard({} as ActivatedRouteSnapshot, { url: '/orders' } as RouterStateSnapshot),
      ),
    ).toBe(true);
  });

  it.each([
    ['ADMIN', true],
    ['INVENTORY_MANAGER', false],
    ['SALES', false],
  ] satisfies readonly (readonly [AppRole, boolean])[])(
    'applies the users policy for %s',
    (role, expected) => {
      roles.set([role]);
      const route = {
        data: { [APP_SECTION_DATA_KEY]: APP_SECTIONS.users },
      } as unknown as ActivatedRouteSnapshot;
      const result = TestBed.runInInjectionContext(() =>
        roleGuard(route, {} as RouterStateSnapshot),
      );

      expect(result === true).toBe(expected);
      if (!expected) {
        expect(TestBed.inject(Router).serializeUrl(result as never)).toBe('/forbidden');
      }
    },
  );

  it('accepts access granted by any one of multiple roles', () => {
    roles.set(['SALES', 'INVENTORY_MANAGER']);
    const route = {
      data: { [APP_SECTION_DATA_KEY]: APP_SECTIONS.suppliers },
    } as unknown as ActivatedRouteSnapshot;

    expect(TestBed.runInInjectionContext(() => roleGuard(route, {} as RouterStateSnapshot))).toBe(
      true,
    );
  });

  it.each([
    ['ADMIN', true],
    ['INVENTORY_MANAGER', true],
    ['SALES', false],
  ] satisfies readonly (readonly [AppRole, boolean])[])(
    'applies the product management policy for %s',
    (role, expected) => {
      roles.set([role]);
      const route = {
        data: { [ALLOWED_ROLES_DATA_KEY]: INVENTORY_MANAGEMENT_ROLES },
      } as unknown as ActivatedRouteSnapshot;
      const result = TestBed.runInInjectionContext(() =>
        allowedRolesGuard(route, {} as RouterStateSnapshot),
      );

      expect(result === true).toBe(expected);
      if (!expected) {
        expect(TestBed.inject(Router).serializeUrl(result as never)).toBe('/forbidden');
      }
    },
  );
});
