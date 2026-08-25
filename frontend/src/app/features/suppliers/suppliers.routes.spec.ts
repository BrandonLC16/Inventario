import {
  ALLOWED_ROLES_DATA_KEY,
  INVENTORY_MANAGEMENT_ROLES,
} from '../../core/navigation/app-navigation';
import { allowedRolesGuard } from '../../core/navigation/session.guards';
import { SUPPLIER_ROUTES } from './suppliers.routes';

describe('supplier routes', () => {
  it('lazily guards list, create, detail and edit with the shared supply role policy', () => {
    expect(SUPPLIER_ROUTES.map((route) => route.path)).toEqual(['', 'new', ':id/edit', ':id']);
    for (const route of SUPPLIER_ROUTES) {
      expect(route.loadComponent).toBeTypeOf('function');
      expect(route.canActivate).toContain(allowedRolesGuard);
      expect(route.data?.[ALLOWED_ROLES_DATA_KEY]).toBe(INVENTORY_MANAGEMENT_ROLES);
    }
  });
});
