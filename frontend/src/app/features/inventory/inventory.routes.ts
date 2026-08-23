import { Routes } from '@angular/router';

import {
  ALLOWED_ROLES_DATA_KEY,
  BREADCRUMB_DATA_KEY,
  INVENTORY_MANAGEMENT_ROLES,
} from '../../core/navigation/app-navigation';
import { allowedRolesGuard } from '../../core/navigation/session.guards';

export const INVENTORY_ROUTES: Routes = [
  {
    path: 'alerts',
    title: 'Alertas de MAIN | Inventario',
    canActivate: [allowedRolesGuard],
    data: {
      [ALLOWED_ROLES_DATA_KEY]: INVENTORY_MANAGEMENT_ROLES,
      [BREADCRUMB_DATA_KEY]: 'Alertas',
      inventoryScope: 'main',
    },
    loadComponent: () =>
      import('./inventory-alerts').then(({ InventoryAlerts }) => InventoryAlerts),
  },
  {
    path: 'kardex',
    title: 'Kardex de MAIN | Inventario',
    canActivate: [allowedRolesGuard],
    data: {
      [ALLOWED_ROLES_DATA_KEY]: INVENTORY_MANAGEMENT_ROLES,
      [BREADCRUMB_DATA_KEY]: 'Kardex',
      inventoryScope: 'main',
    },
    loadComponent: () =>
      import('./inventory-kardex').then(({ InventoryKardex }) => InventoryKardex),
  },
  {
    path: '',
    pathMatch: 'full',
    title: 'Inventario de MAIN | Inventario',
    data: { inventoryScope: 'main' },
    loadComponent: () =>
      import('./inventory-balances').then(({ InventoryBalances }) => InventoryBalances),
  },
];
