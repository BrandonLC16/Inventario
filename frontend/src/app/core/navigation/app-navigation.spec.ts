import {
  APP_ROLES,
  APP_SECTIONS,
  AppRole,
  NAVIGATION_SECTIONS,
  canAccessSection,
  isAppRole,
} from './app-navigation';

describe('app navigation policy', () => {
  const visibleSectionIds = (role: AppRole): string[] =>
    NAVIGATION_SECTIONS.filter((section) => canAccessSection([role], section)).map(
      (section) => section.id,
    );

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
    'shows only the authorized sections for %s',
    (role, expected) => {
      expect(visibleSectionIds(role)).toEqual(expected);
    },
  );

  it('uses the canonical paths from the route plan', () => {
    expect(APP_SECTIONS.purchases.path).toBe('purchase-orders');
    expect(APP_SECTIONS.transfers.path).toBe('inventory-transfers');
    expect(APP_SECTIONS.users.path).toBe('admin/users');
  });

  it('accepts only roles declared by the application', () => {
    expect(APP_ROLES.every((role) => isAppRole(role))).toBe(true);
    expect(isAppRole('WAREHOUSE_ADMIN')).toBe(false);
  });

  it('allows a section when any assigned role is authorized', () => {
    expect(canAccessSection(['SALES', 'INVENTORY_MANAGER'], APP_SECTIONS.suppliers)).toBe(true);
    expect(canAccessSection(['SALES'], APP_SECTIONS.suppliers)).toBe(false);
  });
});
