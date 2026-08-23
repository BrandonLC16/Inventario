import { expect, Page, test } from '@playwright/test';

import { login } from './support/mock-inventory-api';
import {
  HISTORICAL_PRODUCT_ID,
  installMockInventoryOperationsApi,
  NORTH_WAREHOUSE_ID,
} from './support/mock-inventory-operations-api';

test('ADMIN filters and pages MAIN alerts through the server', async ({ page }) => {
  const api = await openInventory(page, 'admin');
  await page.getByRole('link', { name: 'Alertas', exact: true }).click();

  await expect(page.getByRole('heading', { name: 'Alertas de MAIN' })).toBeFocused();
  await expect(page.getByText('MAIN es un almacén, no un agregado multi-almacén.')).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'ALERT-001' })).toHaveCount(1);

  await page.getByLabel('Buscar por SKU o nombre').fill('ALERT');
  await page.getByRole('checkbox', { name: 'Sólo productos agotados' }).check();
  await page.getByRole('button', { name: 'Aplicar filtros' }).click();
  await expect(page).toHaveURL(/search=ALERT/);
  await expect(page).toHaveURL(/outOfStockOnly=true/);
  expect(api.alertRequests().at(-1)).toContain('search=ALERT');
  expect(api.alertRequests().at(-1)).toContain('outOfStockOnly=true');
  await expect(page.getByText('Agotado').first()).toBeVisible();

  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page).toHaveURL(/page=1/);
  expect(api.alertRequests().at(-1)).toContain('page=1');
});

test('warehouse Kardex sends combined filters and preserves deleted-product history without N+1', async ({
  page,
}) => {
  const api = await openInventory(page, 'inventory');
  await page.getByRole('combobox', { name: /Almac/ }).click();
  await page.getByRole('option', { name: /NORTH/ }).click();
  await page.getByRole('link', { name: 'Kardex', exact: true }).click();

  await expect(page.getByRole('heading', { name: 'Kardex de NORTH' })).toBeFocused();
  await page.getByLabel('ID de producto').fill(HISTORICAL_PRODUCT_ID);
  await page.getByRole('combobox', { name: 'Tipo de movimiento' }).click();
  await page.getByRole('option', { name: 'ORDER_RESERVED', exact: true }).click();
  await page.getByLabel('Desde').fill('2026-08-01T00:00');
  await page.getByLabel('Hasta').fill('2026-08-31T23:59');
  await page.getByLabel('Referencia exacta').fill('ORDER-DELETED-1');
  await page.getByRole('button', { name: 'Aplicar filtros' }).click();

  await expect(page).toHaveURL(/productId=90000000/);
  await expect(page).toHaveURL(/type=ORDER_RESERVED/);
  await expect(page).toHaveURL(/reference=ORDER-DELETED-1/);
  const request = api.movementRequests().at(-1) ?? '';
  expect(request).toContain(`/warehouses/${NORTH_WAREHOUSE_ID}/inventory/movements`);
  expect(request).toContain('from=');
  expect(request).toContain('to=');
  await expect(page.getByText(HISTORICAL_PRODUCT_ID)).toBeVisible();
  await expect(page.getByText('Identificador histórico')).toBeVisible();
  await expect(page.getByText('Reserva de pedido')).toBeVisible();
  await expect(page.getByText('+3', { exact: true })).toBeVisible();
  expect(api.productDetailRequests()).toBe(0);
});

test('invalid date range stays on the form and empty/error states are recoverable', async ({
  page,
}) => {
  await installMockInventoryOperationsApi(page, { emptyAlerts: true, failMovements: true });
  await page.goto('/login');
  await login(page, 'admin');
  await page.getByRole('link', { name: 'Inventario', exact: true }).click();
  await page.getByRole('link', { name: 'Alertas', exact: true }).click();
  await expect(page.getByText('No hay alertas con estos filtros')).toBeVisible();

  await page.getByRole('link', { name: 'Kardex', exact: true }).click();
  await expect(page.getByText('Ocurrió un error inesperado')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
  await page.getByLabel('Desde').fill('2026-08-20T10:00');
  await page.getByLabel('Hasta').fill('2026-08-19T10:00');
  await page.getByRole('button', { name: 'Aplicar filtros' }).click();
  await expect(page.getByText('La fecha inicial no puede ser posterior')).toBeVisible();
});

test('SALES is denied both alerts and Kardex routes before an operation request', async ({
  page,
}) => {
  const api = await installMockInventoryOperationsApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await navigateInSession(page, '/inventory/alerts');
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.getByRole('heading', { name: 'Acceso no disponible' })).toBeVisible();

  await navigateInSession(page, `/warehouses/${NORTH_WAREHOUSE_ID}/inventory/kardex`);
  await expect(page).toHaveURL(/\/forbidden$/);
  expect(api.alertRequests()).toHaveLength(0);
  expect(api.movementRequests()).toHaveLength(0);
});

test('alerts and Kardex navigation is keyboard-operable on a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installMockInventoryOperationsApi(page);
  await page.goto('/login');
  await login(page, 'inventory');
  await page.getByRole('button', { name: /Abrir o cerrar/ }).click();
  await page.getByRole('link', { name: 'Inventario', exact: true }).click();

  const alerts = page.getByRole('link', { name: 'Alertas', exact: true });
  await alerts.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Alertas de MAIN' })).toBeFocused();
  await expect(page.getByLabel('Buscar por SKU o nombre')).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});

async function openInventory(page: Page, identifier: 'admin' | 'inventory') {
  const api = await installMockInventoryOperationsApi(page);
  await page.goto('/login');
  await login(page, identifier);
  await page.getByRole('link', { name: 'Inventario', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Inventario de MAIN' })).toBeVisible();
  return api;
}

async function navigateInSession(page: Page, path: string): Promise<void> {
  await page.waitForFunction(() => window.location.pathname !== '/login');
  await page.evaluate((target) => {
    window.history.pushState({}, '', target);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}
