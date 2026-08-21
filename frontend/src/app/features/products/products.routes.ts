import { Routes } from '@angular/router';

import {
  ALLOWED_ROLES_DATA_KEY,
  BREADCRUMB_DATA_KEY,
  INVENTORY_MANAGEMENT_ROLES,
} from '../../core/navigation/app-navigation';
import { allowedRolesGuard } from '../../core/navigation/session.guards';

export const PRODUCT_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Productos | Inventario',
    loadComponent: () => import('./products-list').then(({ ProductsList }) => ProductsList),
  },
  {
    path: 'new',
    title: 'Nuevo producto | Inventario',
    canActivate: [allowedRolesGuard],
    data: {
      [ALLOWED_ROLES_DATA_KEY]: INVENTORY_MANAGEMENT_ROLES,
      [BREADCRUMB_DATA_KEY]: 'Nuevo producto',
      formMode: 'create',
    },
    loadComponent: () => import('./product-form').then(({ ProductForm }) => ProductForm),
  },
  {
    path: ':id/edit',
    title: 'Editar producto | Inventario',
    canActivate: [allowedRolesGuard],
    data: {
      [ALLOWED_ROLES_DATA_KEY]: INVENTORY_MANAGEMENT_ROLES,
      [BREADCRUMB_DATA_KEY]: 'Editar',
      formMode: 'edit',
    },
    loadComponent: () => import('./product-form').then(({ ProductForm }) => ProductForm),
  },
  {
    path: ':id',
    title: 'Detalle de producto | Inventario',
    data: { [BREADCRUMB_DATA_KEY]: 'Detalle' },
    loadComponent: () => import('./product-detail').then(({ ProductDetail }) => ProductDetail),
  },
];
