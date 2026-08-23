import { Route, Routes } from '@angular/router';

import {
  APP_SECTION_DATA_KEY,
  APP_SECTIONS,
  AppSection,
  BREADCRUMB_DATA_KEY,
  NAVIGATION_SECTIONS,
} from './core/navigation/app-navigation';
import { authenticatedGuard, roleGuard } from './core/navigation/session.guards';

const sectionRoutes: Routes = NAVIGATION_SECTIONS.filter(
  (section) => !['products', 'warehouses', 'inventory'].includes(section.id),
).map(
  (section): Route => ({
    path: section.path,
    title: `${section.label} | Inventario`,
    canActivate: [roleGuard],
    data: {
      [APP_SECTION_DATA_KEY]: section satisfies AppSection,
      [BREADCRUMB_DATA_KEY]: section.label,
    },
    loadComponent: () =>
      import('./features/section-placeholder/section-placeholder').then(
        ({ SectionPlaceholder }) => SectionPlaceholder,
      ),
  }),
);

export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión | Inventario',
    loadComponent: () => import('./features/auth/login/login').then(({ Login }) => Login),
  },
  {
    path: '',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./layout/app-shell/app-shell').then(({ AppShell }) => AppShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: APP_SECTIONS.products.path,
        canActivate: [roleGuard],
        data: {
          [APP_SECTION_DATA_KEY]: APP_SECTIONS.products,
          [BREADCRUMB_DATA_KEY]: APP_SECTIONS.products.label,
        },
        loadChildren: () =>
          import('./features/products/products.routes').then(
            ({ PRODUCT_ROUTES }) => PRODUCT_ROUTES,
          ),
      },
      {
        path: APP_SECTIONS.warehouses.path,
        canActivate: [roleGuard],
        data: {
          [APP_SECTION_DATA_KEY]: APP_SECTIONS.warehouses,
          [BREADCRUMB_DATA_KEY]: APP_SECTIONS.warehouses.label,
        },
        loadChildren: () =>
          import('./features/warehouses/warehouses.routes').then(
            ({ WAREHOUSE_ROUTES }) => WAREHOUSE_ROUTES,
          ),
      },
      {
        path: APP_SECTIONS.inventory.path,
        canActivate: [roleGuard],
        data: {
          [APP_SECTION_DATA_KEY]: APP_SECTIONS.inventory,
          [BREADCRUMB_DATA_KEY]: APP_SECTIONS.inventory.label,
        },
        loadChildren: () =>
          import('./features/inventory/inventory.routes').then(
            ({ INVENTORY_ROUTES }) => INVENTORY_ROUTES,
          ),
      },
      ...sectionRoutes,
      {
        path: 'design-system',
        title: 'Sistema visual | Inventario',
        data: { [BREADCRUMB_DATA_KEY]: 'Sistema visual' },
        loadComponent: () =>
          import('./features/design-system/design-system-demo').then(
            ({ DesignSystemDemo }) => DesignSystemDemo,
          ),
      },
      {
        path: 'forbidden',
        title: 'Acceso no disponible | Inventario',
        data: { [BREADCRUMB_DATA_KEY]: 'Acceso no disponible' },
        loadComponent: () =>
          import('./layout/status-pages/forbidden').then(({ Forbidden }) => Forbidden),
      },
      {
        path: '**',
        title: 'Página no encontrada | Inventario',
        data: { [BREADCRUMB_DATA_KEY]: 'Página no encontrada' },
        loadComponent: () =>
          import('./layout/status-pages/not-found').then(({ NotFound }) => NotFound),
      },
    ],
  },
];
