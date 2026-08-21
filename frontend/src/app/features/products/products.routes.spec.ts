import {
  ALLOWED_ROLES_DATA_KEY,
  INVENTORY_MANAGEMENT_ROLES,
} from '../../core/navigation/app-navigation';
import { allowedRolesGuard } from '../../core/navigation/session.guards';
import { PRODUCT_ROUTES } from './products.routes';

describe('product routes', () => {
  it('keeps list and detail readable while guarding create and edit lazily', () => {
    const list = PRODUCT_ROUTES.find((route) => route.path === '');
    const detail = PRODUCT_ROUTES.find((route) => route.path === ':id');
    const create = PRODUCT_ROUTES.find((route) => route.path === 'new');
    const edit = PRODUCT_ROUTES.find((route) => route.path === ':id/edit');

    expect(list?.loadComponent).toBeTypeOf('function');
    expect(detail?.canActivate).toBeUndefined();
    for (const managementRoute of [create, edit]) {
      expect(managementRoute?.canActivate).toContain(allowedRolesGuard);
      expect(managementRoute?.data?.[ALLOWED_ROLES_DATA_KEY]).toBe(INVENTORY_MANAGEMENT_ROLES);
    }
  });
});
