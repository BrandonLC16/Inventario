import { Routes } from '@angular/router';

export const INVENTORY_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Inventario de MAIN | Inventario',
    data: { inventoryScope: 'main' },
    loadComponent: () =>
      import('./inventory-balances').then(({ InventoryBalances }) => InventoryBalances),
  },
];
