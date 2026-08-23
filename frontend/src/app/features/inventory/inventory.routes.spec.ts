import { WAREHOUSE_ROUTES } from '../warehouses/warehouses.routes';
import { INVENTORY_ROUTES } from './inventory.routes';

describe('inventory routes', () => {
  it('loads MAIN and warehouse balances lazily without restricting any authenticated role', () => {
    const main = INVENTORY_ROUTES.find((route) => route.path === '');
    const warehouse = WAREHOUSE_ROUTES.find((route) => route.path === ':id/inventory');

    expect(main?.data?.['inventoryScope']).toBe('main');
    expect(main?.canActivate).toBeUndefined();
    expect(main?.loadComponent).toBeTypeOf('function');
    expect(warehouse?.data?.['inventoryScope']).toBe('warehouse');
    expect(warehouse?.canActivate).toBeUndefined();
    expect(warehouse?.loadComponent).toBeTypeOf('function');
  });

  it('loads manager-only alerts and Kardex for MAIN and each warehouse', () => {
    for (const path of ['alerts', 'kardex']) {
      const main = INVENTORY_ROUTES.find((route) => route.path === path);
      const warehouse = WAREHOUSE_ROUTES.find((route) => route.path === `:id/inventory/${path}`);
      expect(main?.data?.['inventoryScope']).toBe('main');
      expect(main?.data?.['allowedRoles']).toEqual(['ADMIN', 'INVENTORY_MANAGER']);
      expect(main?.canActivate).toHaveLength(1);
      expect(main?.loadComponent).toBeTypeOf('function');
      expect(warehouse?.data?.['inventoryScope']).toBe('warehouse');
      expect(warehouse?.data?.['allowedRoles']).toEqual(['ADMIN', 'INVENTORY_MANAGER']);
      expect(warehouse?.canActivate).toHaveLength(1);
      expect(warehouse?.loadComponent).toBeTypeOf('function');
    }
  });
});
