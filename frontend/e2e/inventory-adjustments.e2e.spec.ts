import { expect, Page, test } from '@playwright/test';

import { login } from './support/mock-inventory-api';
import {
  AdjustmentFailure,
  installMockInventoryBalancesApi,
  NORTH_WAREHOUSE_ID,
} from './support/mock-inventory-balances-api';

test('manager confirms one MAIN entry and the table uses only the API response', async ({
  page,
}) => {
  const api = await openInventory(page, 'admin');
  await openFirstAdjustment(page);

  await page.getByLabel('Cantidad').fill('4');
  await page.getByLabel('Referencia opcional').fill(' RECONTEO-01 ');
  const review = page.getByRole('button', { name: 'Revisar y confirmar' });
  await review.click();
  const dialog = page.getByRole('dialog', { name: 'Confirmar ajuste manual' });
  await expect(dialog).toContainText('MAIN');
  await expect(dialog).toContainText('Ajuste: +4');
  await expect(dialog).toContainText('Resultado previsto: 4');
  await expect(dialog).toContainText('Referencia: RECONTEO-01');

  await dialog.getByRole('button', { name: 'Cancelar' }).click();
  expect(api.adjustmentRequests()).toHaveLength(0);

  await review.evaluate((element: HTMLButtonElement) => {
    element.click();
    element.click();
  });
  await expect(page.getByRole('dialog', { name: 'Confirmar ajuste manual' })).toHaveCount(1);
  const confirm = page.getByRole('button', { name: 'Aplicar ajuste' });
  await confirm.evaluate((element: HTMLButtonElement) => {
    element.click();
    element.click();
  });

  await expect(page.getByText('saldo confirmado por Inventory API')).toBeVisible();
  expect(api.adjustmentRequests()).toEqual([
    {
      path: '/api/v1/inventory/10000000-0000-0000-0000-000000000001/adjustments',
      body: { quantityDelta: 4, reference: 'RECONTEO-01' },
    },
  ]);
  const row = page.getByRole('row').nth(1);
  await expect(row.getByRole('cell', { name: '4', exact: true })).toHaveCount(2);
});

test('warehouse change discards the form and an output stays isolated in NORTH', async ({
  page,
}) => {
  const api = await openInventory(page, 'inventory');
  await openFirstAdjustment(page);
  await page.getByLabel('Cantidad').fill('7');

  await selectNorth(page);
  await expect(page.getByRole('heading', { name: /SKU-001/ })).toHaveCount(0);
  expect(api.adjustmentRequests()).toHaveLength(0);

  await openFirstAdjustment(page);
  await page.getByRole('combobox', { name: 'Tipo de movimiento' }).click();
  await page.getByRole('option', { name: 'Salida', exact: true }).click();
  await page.getByLabel('Cantidad').fill('2');
  await page.getByRole('button', { name: 'Revisar y confirmar' }).click();
  await expect(page.getByRole('dialog')).toContainText('Ajuste: -2');
  await page.getByRole('button', { name: 'Aplicar ajuste' }).click();

  await expect(page.getByText('saldo confirmado por Inventory API')).toBeVisible();
  expect(api.adjustmentRequests()).toEqual([
    {
      path: `/api/v1/warehouses/${NORTH_WAREHOUSE_ID}/inventory/10000000-0000-0000-0000-000000000001/adjustments`,
      body: { quantityDelta: -2 },
    },
  ]);
  await expect(page.getByRole('row').nth(1)).toContainText('99');
});

test('SALES can read balances but cannot start a manual adjustment', async ({ page }) => {
  await openInventory(page, 'sales');

  await expect(page.getByRole('cell', { name: 'SKU-001' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Ajustar stock/ })).toHaveCount(0);
});

for (const scenario of [
  { failure: 400 as const, title: 'La solicitud', guidance: true },
  { failure: 401 as const, title: 'La sesi' },
  { failure: 403 as const, title: 'No tienes permiso' },
  { failure: 'network' as const, title: 'no es seguro asumir el resultado' },
]) {
  test(`a ${scenario.failure} failure preserves context and never retries the adjustment`, async ({
    page,
  }) => {
    const api = await openInventory(page, 'admin', scenario.failure);
    await openAdjustment(page, scenario.guidance ? 'SKU-003' : 'SKU-001');
    if (scenario.guidance) {
      await page.getByRole('combobox', { name: 'Tipo de movimiento' }).click();
      await page.getByRole('option', { name: 'Salida', exact: true }).click();
    }
    await page.getByLabel('Referencia opcional').fill('CONTEXTO-01');
    await page.getByRole('button', { name: 'Revisar y confirmar' }).click();
    await page.getByRole('button', { name: 'Aplicar ajuste' }).click();

    await expect(page.getByText(scenario.title, { exact: false })).toBeVisible();
    await expect(page.getByLabel('Referencia opcional')).toHaveValue('CONTEXTO-01');
    expect(api.adjustmentRequests()).toHaveLength(1);
    if (scenario.failure === 401) {
      expect(api.refreshRequests()).toBe(0);
    }
    if (scenario.guidance) {
      await expect(page.getByText('puede faltar stock disponible', { exact: false })).toBeVisible();
    }
    await expect(page.getByRole('button', { name: /Reintentar/ })).toHaveCount(0);
  });
}

test('429 announces the wait and blocks another adjustment request', async ({ page }) => {
  const api = await openInventory(page, 'admin', 429);
  await openFirstAdjustment(page);
  await page.getByRole('button', { name: 'Revisar y confirmar' }).click();
  await page.getByRole('button', { name: 'Aplicar ajuste' }).click();

  await expect(page.getByText('Espera antes de volver a intentarlo')).toBeVisible();
  const blocked = page.getByRole('button', { name: /Disponible en \d+ s/ });
  await expect(blocked).toBeDisabled();
  await blocked.evaluate((element: HTMLButtonElement) => element.click());
  expect(api.adjustmentRequests()).toHaveLength(1);
});

test('adjustment form is keyboard operable and moves focus to its accessible heading', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await openInventory(page, 'inventory', undefined, true);

  const action = page.getByRole('button', { name: 'Ajustar stock de SKU-001' });
  await action.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: /SKU-001.*Producto 01/ })).toBeFocused();
  await expect(page.getByLabel('Tipo de movimiento')).toBeVisible();
  await expect(page.getByLabel('Cantidad')).toBeVisible();
  await expect(page.getByLabel('Referencia opcional')).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});

async function openInventory(
  page: Page,
  identifier: 'admin' | 'inventory' | 'sales',
  failure?: AdjustmentFailure,
  mobile = false,
) {
  const api = await installMockInventoryBalancesApi(
    page,
    failure === undefined ? {} : { adjustmentFailure: failure },
  );
  await page.goto('/login');
  await login(page, identifier);
  if (mobile) {
    await page.getByRole('button', { name: /Abrir o cerrar/ }).click();
  }
  await page.getByRole('link', { name: 'Inventario', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Inventario de MAIN' })).toBeVisible();
  return api;
}

async function openFirstAdjustment(page: Page): Promise<void> {
  await openAdjustment(page, 'SKU-001');
}

async function openAdjustment(page: Page, sku: string): Promise<void> {
  await page.getByRole('button', { name: `Ajustar stock de ${sku}` }).click();
  await expect(page.getByRole('heading', { name: new RegExp(sku) })).toBeFocused();
}

async function selectNorth(page: Page): Promise<void> {
  await page.getByRole('combobox', { name: /Almac/ }).click();
  await page.getByRole('option', { name: /NORTH/ }).click();
  await expect(page).toHaveURL(new RegExp(`/warehouses/${NORTH_WAREHOUSE_ID}/inventory`));
}
