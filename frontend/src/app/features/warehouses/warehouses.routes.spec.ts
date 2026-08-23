import {
  ALLOWED_ROLES_DATA_KEY,
  INVENTORY_MANAGEMENT_ROLES,
} from '../../core/navigation/app-navigation';
import { allowedRolesGuard } from '../../core/navigation/session.guards';
import { WAREHOUSE_ROUTES } from './warehouses.routes';

describe('warehouse routes', () => {
  it('keeps list and detail readable while guarding create and edit lazily', () => {
    const list = WAREHOUSE_ROUTES.find((route) => route.path === '');
    const detail = WAREHOUSE_ROUTES.find((route) => route.path === ':id');
    const create = WAREHOUSE_ROUTES.find((route) => route.path === 'new');
    const edit = WAREHOUSE_ROUTES.find((route) => route.path === ':id/edit');

    expect(list?.loadComponent).toBeTypeOf('function');
    expect(detail?.canActivate).toBeUndefined();
    for (const managementRoute of [create, edit]) {
      expect(managementRoute?.canActivate).toContain(allowedRolesGuard);
      expect(managementRoute?.data?.[ALLOWED_ROLES_DATA_KEY]).toBe(INVENTORY_MANAGEMENT_ROLES);
    }
  });
});
