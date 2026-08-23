import { expect, test } from '@playwright/test';

import { login } from './support/mock-inventory-api';
import {
  installMockInventoryBalancesApi,
  NORTH_WAREHOUSE_ID,
} from './support/mock-inventory-balances-api';

test('MAIN is explicit, composes reordered product metadata and pages remotely', async ({
  page,
}) => {
  const api = await installMockInventoryBalancesApi(page);
  await page.goto('/login');
  await login(page, 'admin');
  await page.getByRole('link', { name: 'Inventario', exact: true }).click();

  await expect(page.getByRole('heading', { name: 'Inventario de MAIN' })).toBeFocused();
  await expect(page.getByText('MAIN es un almacén, no un total multi-almacén.')).toBeVisible();
  await expect(page.getByText('/api/v1/inventory')).toBeVisible();
  const firstRow = page.getByRole('row').nth(1);
  await expect(firstRow).toContainText('SKU-001');
  await expect(firstRow).toContainText('Producto 01');
  await expect(firstRow).toContainText('Sin movimientos');
  await expect(firstRow.getByRole('cell', { name: '0', exact: true })).toHaveCount(3);
  expect(api.balanceRequests().at(-1)).toBe('/api/v1/inventory?page=0&size=20');
  expect(api.settingsRequests().at(-1)).toContain('/inventory/settings?page=0&size=20');

  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page).toHaveURL(/page=1/);
  await expect(page.getByRole('cell', { name: 'SKU-021' })).toBeVisible();
  expect(api.balanceRequests().at(-1)).toBe('/api/v1/inventory?page=1&size=20');
});

test('warehouse selector isolates scoped balances and returns MAIN to its canonical route', async ({
  page,
}) => {
  await installMockInventoryBalancesApi(page);
  await page.goto('/login');
  await login(page, 'inventory');
  await page.getByRole('link', { name: 'Inventario', exact: true }).click();

  await page.getByRole('combobox', { name: 'Almacén' }).click();
  await page.getByRole('option', { name: /NORTH/ }).click();
  await expect(page).toHaveURL(new RegExp(`/warehouses/${NORTH_WAREHOUSE_ID}/inventory`));
  await expect(
    page.getByText('Consulta saldos físicos, reservados y disponibles de NORTH.'),
  ).toBeVisible();
  const northFirstRow = page.getByRole('row').nth(1);
  await expect(northFirstRow).toContainText('101');
  await expect(northFirstRow).not.toContainText('Sin movimientos');

  await page.getByRole('combobox', { name: 'Almacén' }).click();
  await page.getByRole('option', { name: /MAIN · almacén compatible/ }).click();
  await expect(page).toHaveURL(/\/inventory$/);
  await expect(page.getByRole('row').nth(1)).toContainText('Sin movimientos');
});

for (const identifier of ['admin', 'inventory', 'sales']) {
  test(`${identifier} can read inventory balances`, async ({ page }) => {
    await installMockInventoryBalancesApi(page);
    await page.goto('/login');
    await login(page, identifier);
    await page.getByRole('link', { name: 'Inventario', exact: true }).click();

    await expect(page.getByRole('heading', { name: 'Inventario de MAIN' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'SKU-001' })).toBeVisible();
  });
}

test('inventory selector and pagination are keyboard operable on mobile without overflow', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installMockInventoryBalancesApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await page.getByRole('button', { name: 'Abrir o cerrar navegación' }).click();
  await page.getByRole('link', { name: 'Inventario', exact: true }).click();

  const warehouseSelector = page.getByRole('combobox', { name: 'Almacén' });
  await expect(warehouseSelector).toBeEnabled();
  await warehouseSelector.focus();
  await page.keyboard.press('Enter');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(new RegExp(`/warehouses/${NORTH_WAREHOUSE_ID}/inventory`));
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});
