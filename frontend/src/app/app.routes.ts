import { Route, Routes } from '@angular/router';

import {
  APP_SECTION_DATA_KEY,
  AppSection,
  BREADCRUMB_DATA_KEY,
  NAVIGATION_SECTIONS,
} from './core/navigation/app-navigation';
import { demoRoleGuard } from './core/navigation/demo-role.guard';

const sectionRoutes: Routes = NAVIGATION_SECTIONS.map(
  (section): Route => ({
    path: section.path,
    title: `${section.label} | Inventario`,
    canActivate: [demoRoleGuard],
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
    path: '',
    loadComponent: () => import('./layout/app-shell/app-shell').then(({ AppShell }) => AppShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      ...sectionRoutes,
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
