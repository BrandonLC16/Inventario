import { expect, test } from '@playwright/test';

import { login } from './support/mock-inventory-api';
import { installMockSuppliersApi } from './support/mock-suppliers-api';

test('INVENTORY_MANAGER filters, pages and completes normalized CRUD with one 204 deactivation', async ({
  page,
}) => {
  const api = await installMockSuppliersApi(
    page,
    {},
    { createDelayMs: 100, deactivateDelayMs: 100 },
  );
  await page.goto('/login');
  await login(page, 'inventory');
  await page.getByRole('link', { name: 'Proveedores' }).click();

  await expect(page.getByRole('heading', { name: 'Proveedores' })).toBeFocused();
  await expect(page.getByRole('row')).toHaveCount(21);
  await page.getByLabel('Código').fill('SUP-002');
  await page.getByLabel('Nombre o razón social').fill('Proveedor');
  await page.getByLabel('Identificador fiscal').fill('RFC002');
  await page.getByLabel('Estado').click();
  await page.getByRole('option', { name: 'Activos', exact: true }).click();
  await page.getByRole('button', { name: 'Aplicar filtros' }).click();
  await expect(page).toHaveURL(
    /code=SUP-002.*name=Proveedor.*fiscalIdentifier=RFC002.*active=true/,
  );
  await expect(page.getByRole('cell', { name: 'SUP-002' })).toBeVisible();

  await page.getByRole('button', { name: 'Limpiar' }).click();
  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page).toHaveURL(/page=1/);
  await page.getByRole('row').nth(1).getByRole('link', { name: 'Ver' }).click();
  await expect(page.getByRole('heading', { name: 'Detalle de proveedor' })).toBeFocused();
  await expect(page.getByText(/20 ago 2026/).first()).toBeVisible();
  await page.getByRole('link', { name: 'Volver al listado' }).click();
  await expect(page).toHaveURL(/page=1/);

  await page.getByRole('link', { name: 'Nuevo proveedor' }).click();
  await expect(page.getByRole('heading', { name: 'Nuevo proveedor' })).toBeFocused();
  await expect(page.getByText(/código y el identificador fiscal/)).toBeVisible();
  await page.getByLabel('Código').fill(' sup-new ');
  await page.getByLabel('Razón social').fill('Proveedor Nuevo');
  await page.getByLabel('Nombre comercial').fill('Comercial Nuevo');
  await page.getByLabel('Identificador fiscal').fill(' rfcnew0101aa1 ');
  await page.getByLabel('Correo').fill(' COMPRAS.NEW@EXAMPLE.TEST ');
  await page.getByRole('button', { name: 'Guardar proveedor' }).evaluate((button) => {
    (button as HTMLButtonElement).click();
    (button as HTMLButtonElement).click();
  });
  await expect(page.getByText(/se creó correctamente con los valores normalizados/)).toBeVisible();
  expect(api.createBodies()).toHaveLength(1);
  expect(api.createBodies()[0]).toMatchObject({
    code: 'SUP-NEW',
    fiscalIdentifier: 'RFCNEW0101AA1',
    email: 'compras.new@example.test',
  });
  await expect(page.getByText('SUP-NEW', { exact: true })).toBeVisible();
  await expect(page.getByText('RFCNEW0101AA1', { exact: true })).toBeVisible();
  await expect(page.getByText('compras.new@example.test', { exact: true })).toBeVisible();

  await page.getByRole('link', { name: 'Editar proveedor' }).click();
  await page.getByLabel('Razón social').fill('Proveedor Actualizado');
  await page.getByRole('button', { name: 'Guardar proveedor' }).click();
  await expect(page.getByText(/cambios del proveedor se guardaron y reconciliaron/)).toBeVisible();
  expect(api.updateBodies().at(-1)).toMatchObject({ legalName: 'Proveedor Actualizado' });

  await page.getByRole('button', { name: 'Desactivar' }).evaluate((button) => {
    (button as HTMLButtonElement).click();
    (button as HTMLButtonElement).click();
  });
  await expect(page.getByRole('dialog')).toHaveCount(1);
  await expect(page.getByRole('dialog')).toContainText('preferencias de abastecimiento');
  await expect(page.getByRole('dialog')).toContainText('historial de compras');
  await page.getByRole('dialog').getByRole('button', { name: 'Desactivar' }).click();
  await expect(page.getByText(/se desactivó correctamente/)).toBeVisible();
  await expect(
    page.getByRole('row').filter({ hasText: 'SUP-NEW' }).getByText('Inactivo', { exact: true }),
  ).toBeVisible();
  expect(api.deactivateRequests()).toBe(1);
});

for (const field of ['code', 'fiscalIdentifier', 'email'] as const) {
  test(`a duplicate supplier ${field} shows the stable conflict and support reference`, async ({
    page,
  }) => {
    await installMockSuppliersApi(page, {}, { conflictField: field });
    await page.goto('/login');
    await login(page, 'admin');
    await page.getByRole('link', { name: 'Proveedores' }).click();
    await page.getByRole('link', { name: 'Nuevo proveedor' }).click();
    await expect(page.getByRole('heading', { name: 'Nuevo proveedor' })).toBeFocused();
    await page.getByLabel('Código').fill('SUP-DUP');
    await page.getByLabel('Razón social').fill('Proveedor duplicado');
    await page.getByRole('button', { name: 'Guardar proveedor' }).click();

    await expect(page.getByRole('alert')).toContainText('La operación entra en conflicto');
    await expect(page.getByText(/deben ser únicos/)).toBeVisible();
    await expect(page.getByLabel('Referencia para soporte')).toHaveValue(
      `duplicate-supplier-${field}`,
    );
    await expect(page.getByRole('alert')).not.toContainText(`Internal duplicate ${field}`);
  });
}

test('SALES is rejected before supplier network access', async ({ page }) => {
  const api = await installMockSuppliersApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await expect(page.getByRole('link', { name: 'Proveedores' })).toHaveCount(0);

  await page.evaluate(() => {
    window.history.pushState({}, '', '/suppliers/missing');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.locator('[data-error-source="routing"]')).toContainText('403');
  expect(api.listRequests()).toBe(0);
});

test('a supplier 404 is presented as a recoverable detail state', async ({ page }) => {
  await installMockSuppliersApi(page);
  await page.goto('/login');
  await login(page, 'inventory');
  await page.getByRole('link', { name: 'Proveedores' }).click();
  await expect(page.getByRole('heading', { name: 'Proveedores' })).toBeFocused();
  await page.evaluate(() => {
    window.history.pushState({}, '', '/suppliers/missing');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByRole('alert')).toContainText('No se encontró el recurso');
  await expect(page.getByLabel('Referencia para soporte')).toHaveValue('supplier-not-found-e2e');
  await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
});

test('supplier filters are keyboard operable without horizontal mobile overflow', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installMockSuppliersApi(page);
  await page.goto('/login');
  await login(page, 'inventory');
  await page.getByRole('button', { name: 'Abrir o cerrar navegación' }).click();
  await page.getByRole('link', { name: 'Proveedores' }).click();
  await expect(page.getByRole('heading', { name: 'Proveedores' })).toBeFocused();

  await page.getByLabel('Código').focus();
  await page.keyboard.type('SUP-001');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/code=SUP-001/);
  await expect(page.getByRole('cell', { name: 'SUP-001' })).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});
