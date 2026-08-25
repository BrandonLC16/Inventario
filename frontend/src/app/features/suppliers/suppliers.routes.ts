import { Routes } from '@angular/router';

import {
  ALLOWED_ROLES_DATA_KEY,
  BREADCRUMB_DATA_KEY,
  INVENTORY_MANAGEMENT_ROLES,
} from '../../core/navigation/app-navigation';
import { allowedRolesGuard } from '../../core/navigation/session.guards';

const supplierRouteAccess = {
  canActivate: [allowedRolesGuard],
  data: { [ALLOWED_ROLES_DATA_KEY]: INVENTORY_MANAGEMENT_ROLES },
};

export const SUPPLIER_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Proveedores | Inventario',
    ...supplierRouteAccess,
    loadComponent: () => import('./suppliers-list').then(({ SuppliersList }) => SuppliersList),
  },
  {
    path: 'new',
    title: 'Nuevo proveedor | Inventario',
    ...supplierRouteAccess,
    data: {
      ...supplierRouteAccess.data,
      [BREADCRUMB_DATA_KEY]: 'Nuevo proveedor',
      formMode: 'create',
    },
    loadComponent: () => import('./supplier-form').then(({ SupplierForm }) => SupplierForm),
  },
  {
    path: ':id/edit',
    title: 'Editar proveedor | Inventario',
    ...supplierRouteAccess,
    data: {
      ...supplierRouteAccess.data,
      [BREADCRUMB_DATA_KEY]: 'Editar',
      formMode: 'edit',
    },
    loadComponent: () => import('./supplier-form').then(({ SupplierForm }) => SupplierForm),
  },
  {
    path: ':id',
    title: 'Detalle de proveedor | Inventario',
    ...supplierRouteAccess,
    data: {
      ...supplierRouteAccess.data,
      [BREADCRUMB_DATA_KEY]: 'Detalle',
    },
    loadComponent: () => import('./supplier-detail').then(({ SupplierDetail }) => SupplierDetail),
  },
];
