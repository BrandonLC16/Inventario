import { expect, test } from '@playwright/test';

import { login } from './support/mock-inventory-api';
import { installMockWarehousesApi } from './support/mock-warehouses-api';

test('ADMIN pages and completes warehouse create, edit and confirmed deactivation', async ({
  page,
}) => {
  const api = await installMockWarehousesApi(page);
  await page.goto('/login');
  await login(page, 'admin');
  await page.getByRole('link', { name: 'Almacenes' }).click();

  await expect(page.getByRole('heading', { name: 'Almacenes' })).toBeFocused();
  await expect(page.getByRole('row')).toHaveCount(21);
  expect(api.listQueries().at(-1)).toBe('?page=0&size=20');
  await expect(page.getByRole('searchbox')).toHaveCount(0);

  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page).toHaveURL(/page=1/);
  expect(api.listQueries().at(-1)).toBe('?page=1&size=20');
  await page.getByRole('row').nth(1).getByRole('link', { name: 'Ver' }).click();
  await expect(page.getByRole('heading', { name: 'Detalle de almacén' })).toBeFocused();
  await page.getByRole('link', { name: 'Volver al listado' }).click();
  await expect(page).toHaveURL(/page=1/);

  await page.getByRole('link', { name: 'Nuevo almacén' }).click();
  await expect(page.getByRole('heading', { name: 'Nuevo almacén' })).toBeFocused();
  await page.getByLabel('Código').fill('north');
  await page.getByLabel('Nombre').fill('Almacén norte');
  await page.getByLabel('Descripción').fill('Creado por E2E');
  await page.getByRole('button', { name: 'Guardar almacén' }).click();
  await expect(page.getByText('El almacén se creó correctamente.')).toBeVisible();
  expect(api.createBodies().at(-1)).toMatchObject({ code: 'north', active: true });
  await expect(page.getByText('NORTH')).toBeVisible();

  await page.getByRole('link', { name: 'Editar almacén' }).click();
  await expect(page.getByRole('heading', { name: 'Editar almacén' })).toBeFocused();
  await page.getByLabel('Nombre').fill('Almacén norte actualizado');
  await page.getByRole('button', { name: 'Guardar almacén' }).click();
  await expect(page.getByText('Los cambios del almacén se guardaron correctamente.')).toBeVisible();
  expect(api.updateBodies().at(-1)).toMatchObject({ name: 'Almacén norte actualizado' });

  await page.getByRole('button', { name: 'Desactivar' }).click();
  await expect(page.getByRole('dialog')).toContainText('No podrá recibir nuevas operaciones');
  await page.getByRole('dialog').getByRole('button', { name: 'Desactivar' }).click();
  await expect(page.getByText('El almacén se desactivó correctamente.')).toBeVisible();
  expect(api.deactivateRequests()).toBe(1);
});

test('duplicate code renders the stable conflict and a support reference', async ({ page }) => {
  await installMockWarehousesApi(page);
  await page.goto('/login');
  await login(page, 'inventory');
  await page.getByRole('link', { name: 'Almacenes' }).click();
  await page.getByRole('link', { name: 'Nuevo almacén' }).click();
  await page.getByLabel('Código').fill('main');
  await page.getByLabel('Nombre').fill('Duplicado');
  await page.getByRole('button', { name: 'Guardar almacén' }).click();

  await expect(page.getByRole('alert')).toContainText('La operación entra en conflicto');
  await expect(page.getByLabel('Referencia para soporte')).toHaveValue('duplicate-warehouse-e2e');
  await expect(page.getByRole('alert')).not.toContainText('Internal duplicate warehouse');
});

test('404 detail and blocked deactivation render complete stable error states', async ({
  page,
}) => {
  await installMockWarehousesApi(page);
  await page.goto('/login');
  await login(page, 'admin');

  await page.evaluate(() => {
    window.history.pushState({}, '', '/warehouses/missing');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByRole('alert')).toContainText('No se encontró el recurso');
  await expect(page.getByLabel('Referencia para soporte')).toHaveValue('warehouse-not-found-e2e');

  await page.getByRole('link', { name: 'Volver al listado' }).click();
  await page.getByRole('row').nth(1).getByRole('link', { name: 'Ver' }).click();
  await expect(page.getByText('Almacén principal')).toBeVisible();
  await page.getByRole('button', { name: 'Desactivar' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Desactivar' }).click();
  await expect(page.getByRole('alert')).toContainText('La operación entra en conflicto');
  await expect(page.getByLabel('Referencia para soporte')).toHaveValue(
    'warehouse-stock-conflict-e2e',
  );
  await expect(page.getByRole('alert')).not.toContainText('Internal stock balance');
});

test('SALES can read warehouses but cannot see or enter management actions', async ({ page }) => {
  await installMockWarehousesApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await page.getByRole('link', { name: 'Almacenes' }).click();

  await expect(page.getByRole('link', { name: 'Nuevo almacén' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Editar' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Desactivar' })).toHaveCount(0);
  await page.getByRole('row').nth(1).getByRole('link', { name: 'Ver' }).click();
  await expect(page.getByText('Almacén principal')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Editar almacén' })).toHaveCount(0);

  await page.evaluate(() => {
    window.history.pushState({}, '', '/warehouses/new');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.locator('[data-error-source="routing"]')).toContainText('403');
});

test('warehouse list is keyboard operable and fits a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installMockWarehousesApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await page.getByRole('button', { name: 'Abrir o cerrar navegación' }).click();
  await page.getByRole('link', { name: 'Almacenes' }).click();
  await expect(page.getByRole('heading', { name: 'Almacenes' })).toBeFocused();

  await page.getByRole('button', { name: 'Siguiente' }).focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/page=1/);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});
