import { expect, test } from '@playwright/test';

import { login } from './support/mock-inventory-api';
import { installMockProductsApi } from './support/mock-products-api';

test('ADMIN filters, pages and completes product create, suspension and logical deletion once', async ({
  page,
}) => {
  const api = await installMockProductsApi(page, {}, { deleteDelayMs: 100 });
  await page.goto('/login');
  await login(page, 'admin');
  await page.getByRole('link', { name: 'Productos' }).click();

  await expect(page.getByRole('heading', { name: 'Productos' })).toBeFocused();
  await expect(page.getByRole('row')).toHaveCount(21);
  await page.getByLabel('SKU').fill('SKU-002');
  await page.getByLabel('Nombre').fill('Producto');
  await page.getByLabel('Estado').click();
  await page.getByRole('option', { name: 'Activos', exact: true }).click();
  await page.getByRole('button', { name: 'Aplicar filtros' }).click();
  await expect(page).toHaveURL(/sku=SKU-002.*name=Producto.*active=true/);
  await expect(page.getByRole('cell', { name: 'SKU-002' })).toBeVisible();

  await page.getByRole('button', { name: 'Limpiar' }).click();
  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page).toHaveURL(/page=1/);
  await page.getByRole('row').nth(1).getByRole('link', { name: 'Ver' }).click();
  await expect(page.getByRole('heading', { name: 'Detalle de producto' })).toBeFocused();
  await page.getByRole('link', { name: 'Volver al listado' }).click();
  await expect(page).toHaveURL(/page=1/);

  await page.getByRole('link', { name: 'Nuevo producto' }).click();
  await expect(page.getByRole('heading', { name: 'Nuevo producto' })).toBeFocused();
  await page.getByLabel('SKU').fill('SKU-NEW');
  await page.getByLabel('Nombre').fill('Producto nuevo');
  await page.getByLabel('Descripción').fill('Creado por E2E');
  await page.getByLabel('Precio').fill('25.50');
  await page.getByLabel('Stock mínimo inicial en MAIN').fill('8');
  await page.getByRole('button', { name: 'Guardar producto' }).click();
  await expect(page.getByText('El producto se creó correctamente.')).toBeVisible();
  expect(api.createBodies().at(-1)).toMatchObject({ sku: 'SKU-NEW', minimumStock: 8 });

  await page.getByRole('link', { name: 'Editar producto' }).click();
  await expect(page.getByRole('heading', { name: 'Editar producto' })).toBeFocused();
  await expect(page.getByLabel('Stock mínimo inicial en MAIN')).toHaveCount(0);
  await page.getByLabel('Nombre').fill('Producto actualizado');
  await page.getByLabel('Producto activo (desmarcar sólo lo suspende; no lo da de baja)').uncheck();
  await page.getByRole('button', { name: 'Guardar producto' }).click();
  await expect(
    page.getByText('Los cambios del producto se guardaron correctamente.'),
  ).toBeVisible();
  expect(api.updateBodies().at(-1)).not.toHaveProperty('minimumStock');
  expect(api.updateBodies().at(-1)).toMatchObject({ active: false });
  await expect(page.getByText('Suspendido', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Editar producto' })).toBeVisible();

  await page.getByRole('button', { name: 'Dar de baja' }).evaluate((button) => {
    (button as HTMLButtonElement).click();
    (button as HTMLButtonElement).click();
  });
  await expect(page.getByRole('dialog')).toHaveCount(1);
  await expect(page.getByRole('dialog')).toContainText('no libera el SKU');
  await expect(page.getByRole('dialog')).toContainText('active=false');
  await page.getByRole('dialog').getByRole('button', { name: 'Dar de baja' }).click();
  await expect(page.getByText('El producto se dio de baja lógica correctamente.')).toBeVisible();
  await expect(page.getByText('ID histórico:')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Consultar Kardex histórico' })).toHaveAttribute(
    'href',
    /productId=/,
  );
  expect(api.deleteRequests()).toBe(1);
});

for (const conflict of [
  {
    caseName: 'stock físico',
    message: 'A product with physical inventory cannot be deleted',
    correlationId: 'delete-stock-e2e',
  },
  {
    caseName: 'reservas',
    message: 'A product with inventory reservations cannot be deleted',
    correlationId: 'delete-reservation-e2e',
  },
  {
    caseName: 'documentos pendientes',
    message: 'A product used by pending operations cannot be deleted',
    correlationId: 'delete-document-e2e',
  },
]) {
  test(`a ${conflict.caseName} conflict keeps the product and renders only generic review guidance`, async ({
    page,
  }) => {
    const api = await installMockProductsApi(
      page,
      {},
      {
        deleteConflict: conflict,
      },
    );
    await page.goto('/login');
    await login(page, 'inventory');
    await page.getByRole('link', { name: 'Productos' }).click();

    const firstRow = page.getByRole('row').nth(1);
    await firstRow.getByRole('button', { name: 'Dar de baja' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Dar de baja' }).click();

    await expect(page.getByRole('alert')).toContainText('La operación entra en conflicto');
    await expect(page.getByText('El producto se conserva')).toBeVisible();
    await expect(
      page.getByText('Puede existir stock físico, una reserva o un documento pendiente'),
    ).toBeVisible();
    await expect(page.getByLabel('Referencia para soporte')).toHaveValue(conflict.correlationId);
    await expect(page.getByText(conflict.message)).toHaveCount(0);
    await expect(firstRow.getByRole('cell', { name: 'SKU-001' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Revisar inventario MAIN' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Revisar otros almacenes' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Revisar Kardex por ID' })).toHaveAttribute(
      'href',
      /productId=/,
    );
    expect(api.deleteRequests()).toBe(1);
  });
}

test('duplicate SKU renders the stable conflict and a support reference', async ({ page }) => {
  await installMockProductsApi(page);
  await page.goto('/login');
  await login(page, 'inventory');
  await page.getByRole('link', { name: 'Productos' }).click();
  await page.getByRole('link', { name: 'Nuevo producto' }).click();
  await expect(page.getByRole('heading', { name: 'Nuevo producto' })).toBeFocused();
  await page.getByLabel('SKU').fill('SKU-001');
  await page.getByLabel('Nombre').fill('Duplicado');
  await page.getByLabel('Precio').fill('1.00');
  await page.getByRole('button', { name: 'Guardar producto' }).click();

  await expect(page.getByRole('alert')).toContainText('La operación entra en conflicto');
  await expect(page.getByLabel('Referencia para soporte')).toHaveValue('duplicate-product-e2e');
  await expect(page.getByRole('alert')).not.toContainText('Internal duplicate constraint detail');
});

test('SALES can read products but cannot see or enter management actions', async ({ page }) => {
  await installMockProductsApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await page.getByRole('link', { name: 'Productos' }).click();

  await expect(page.getByRole('link', { name: 'Nuevo producto' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Editar' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Dar de baja' })).toHaveCount(0);
  await page.getByRole('row').nth(1).getByRole('link', { name: 'Ver' }).click();
  await expect(page.getByText('Producto 01')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Editar producto' })).toHaveCount(0);

  await page.evaluate(() => {
    window.history.pushState({}, '', '/products/new');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.locator('[data-error-source="routing"]')).toContainText('403');
});

test('product list is operable by keyboard and fits a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installMockProductsApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await page.getByRole('button', { name: 'Abrir o cerrar navegación' }).click();
  await page.getByRole('link', { name: 'Productos' }).click();
  await expect(page.getByRole('heading', { name: 'Productos' })).toBeFocused();

  await page.getByLabel('SKU').focus();
  await page.keyboard.type('SKU-001');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/sku=SKU-001/);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});
