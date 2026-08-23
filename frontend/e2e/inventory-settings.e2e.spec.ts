import { expect, test } from '@playwright/test';

import { login } from './support/mock-inventory-api';
import {
  installMockInventorySettingsApi,
  SETTINGS_NORTH_WAREHOUSE_ID,
  SETTINGS_SOUTH_WAREHOUSE_ID,
} from './support/mock-inventory-settings-api';

test('manager validates, prevents double submit and reconciles the row after 204', async ({
  page,
}) => {
  const api = await installMockInventorySettingsApi(page);
  await page.goto('/login');
  await login(page, 'inventory');
  await navigateInSession(page, `/warehouses/${SETTINGS_NORTH_WAREHOUSE_ID}/settings`);

  await expect(page.getByRole('heading', { name: 'Mínimos y activación' })).toBeFocused();
  await expect(page.getByText('Dos estados independientes.')).toBeVisible();
  expect(api.listRequests().at(-1)).toContain('page=0&size=20');

  const firstRow = page.getByRole('row').nth(1);
  await expect(firstRow).toContainText('SETTING-001');
  await expect(firstRow.getByRole('cell', { name: '0', exact: true })).toBeVisible();
  await firstRow.getByRole('button', { name: /Configurar/ }).click();
  await expect(page.getByText('Inactivo en catálogo')).toBeVisible();
  await expect(
    page.locator('.settings-editor').getByText('Activo aquí', { exact: true }),
  ).toBeVisible();

  const minimum = page.getByRole('spinbutton', { name: 'Stock mínimo' });
  await minimum.fill('-1');
  await page.getByRole('button', { name: 'Guardar configuración' }).click();
  await expect(page.getByText('Usa un número entero entre 0 y 2147483647.')).toBeVisible();
  expect(api.configureRequests()).toHaveLength(0);

  await minimum.fill('7');
  const save = page.getByRole('button', { name: 'Guardar configuración' });
  await save.evaluate((button: HTMLButtonElement) => {
    button.click();
    button.click();
  });
  await expect(page.getByText('La configuración se guardó y se reconcilió')).toBeVisible();
  expect(api.configureRequests()).toHaveLength(1);
  expect(api.settingDetailRequests()).toBe(2);
  await expect(firstRow.getByRole('cell', { name: '7', exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page).toHaveURL(/page=1/);
  await expect(page.getByRole('cell', { name: 'SETTING-021', exact: true })).toBeVisible();
  expect(api.listRequests().at(-1)).toContain('page=1&size=20');
});

test('409 on deactivation preserves the form and reports stock/reservation guidance', async ({
  page,
}) => {
  const api = await installMockInventorySettingsApi(page, { conflictOnDeactivate: true });
  await page.goto('/login');
  await login(page, 'admin');
  await navigateInSession(page, `/warehouses/${SETTINGS_NORTH_WAREHOUSE_ID}/settings`);

  await page
    .getByRole('row')
    .nth(1)
    .getByRole('button', { name: /Configurar/ })
    .click();
  await page.getByRole('spinbutton', { name: 'Stock mínimo' }).fill('9');
  await page.getByRole('checkbox', { name: /Producto habilitado/ }).uncheck();
  await page.getByRole('button', { name: 'Guardar configuración' }).click();

  await expect(page.getByText('stock físico o las reservas')).toBeVisible();
  await expect(page.locator('input[readonly]')).toHaveValue('settings-stock-conflict');
  await expect(page.getByRole('spinbutton', { name: 'Stock mínimo' })).toHaveValue('9');
  await expect(page.getByRole('checkbox', { name: /Producto habilitado/ })).not.toBeChecked();
  expect(api.configureRequests()).toHaveLength(1);
});

for (const identifier of ['admin', 'inventory', 'sales']) {
  test(`${identifier} can read settings with role-appropriate controls`, async ({ page }) => {
    await installMockInventorySettingsApi(page);
    await page.goto('/login');
    await login(page, identifier);
    await navigateInSession(page, `/warehouses/${SETTINGS_NORTH_WAREHOUSE_ID}/settings`);

    await expect(page.getByRole('cell', { name: 'SETTING-001', exact: true })).toBeVisible();
    const row = page.getByRole('row').nth(1);
    await row
      .getByRole('button', {
        name: identifier === 'sales' ? /Consultar estados/ : /Configurar/,
      })
      .click();
    if (identifier === 'sales') {
      await expect(page.getByText('Tienes acceso de consulta')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Guardar configuración' })).toHaveCount(0);
    } else {
      await expect(page.getByRole('button', { name: 'Guardar configuración' })).toBeVisible();
    }
  });
}

test('warehouse settings remain isolated and keyboard-operable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installMockInventorySettingsApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await navigateInSession(page, `/warehouses/${SETTINGS_NORTH_WAREHOUSE_ID}/settings`);
  await expect(page.getByRole('row').nth(1)).toContainText('0');

  await navigateInSession(page, `/warehouses/${SETTINGS_SOUTH_WAREHOUSE_ID}/settings`);
  const firstRow = page.getByRole('row').nth(1);
  await expect(firstRow).toContainText('100');
  const inspect = firstRow.getByRole('button', { name: /Consultar estados/ });
  await inspect.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: /SETTING-001/ })).toBeFocused();
  await expect(page.getByText('Estado en SOUTH')).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});

async function navigateInSession(
  page: import('@playwright/test').Page,
  path: string,
): Promise<void> {
  await page.evaluate((target) => {
    window.history.pushState({}, '', target);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
  await expect(page).toHaveURL(new RegExp(path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
}
