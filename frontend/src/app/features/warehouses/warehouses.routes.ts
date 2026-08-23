import { Routes } from '@angular/router';

import {
  ALLOWED_ROLES_DATA_KEY,
  BREADCRUMB_DATA_KEY,
  INVENTORY_MANAGEMENT_ROLES,
} from '../../core/navigation/app-navigation';
import { allowedRolesGuard } from '../../core/navigation/session.guards';

export const WAREHOUSE_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Almacenes | Inventario',
    loadComponent: () => import('./warehouses-list').then(({ WarehousesList }) => WarehousesList),
  },
  {
    path: 'new',
    title: 'Nuevo almacén | Inventario',
    canActivate: [allowedRolesGuard],
    data: {
      [ALLOWED_ROLES_DATA_KEY]: INVENTORY_MANAGEMENT_ROLES,
      [BREADCRUMB_DATA_KEY]: 'Nuevo almacén',
      formMode: 'create',
    },
    loadComponent: () => import('./warehouse-form').then(({ WarehouseForm }) => WarehouseForm),
  },
  {
    path: ':id/edit',
    title: 'Editar almacén | Inventario',
    canActivate: [allowedRolesGuard],
    data: {
      [ALLOWED_ROLES_DATA_KEY]: INVENTORY_MANAGEMENT_ROLES,
      [BREADCRUMB_DATA_KEY]: 'Editar',
      formMode: 'edit',
    },
    loadComponent: () => import('./warehouse-form').then(({ WarehouseForm }) => WarehouseForm),
  },
  {
    path: ':id',
    title: 'Detalle de almacén | Inventario',
    data: { [BREADCRUMB_DATA_KEY]: 'Detalle' },
    loadComponent: () =>
      import('./warehouse-detail').then(({ WarehouseDetail }) => WarehouseDetail),
  },
];
