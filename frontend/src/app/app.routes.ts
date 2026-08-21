import { Route, Routes } from '@angular/router';

import {
  APP_SECTION_DATA_KEY,
  AppSection,
  BREADCRUMB_DATA_KEY,
  NAVIGATION_SECTIONS,
} from './core/navigation/app-navigation';
import { authenticatedGuard, roleGuard } from './core/navigation/session.guards';

const sectionRoutes: Routes = NAVIGATION_SECTIONS.map(
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
