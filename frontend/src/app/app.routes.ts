import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./welcome/welcome').then(({ Welcome }) => Welcome),
    title: 'Inventario',
  },
];
