import { expect, test } from '@playwright/test';

import { ApiFailure, installMockInventoryApi, login } from './support/mock-inventory-api';

const safeFailures: readonly {
  name: string;
  failure: ApiFailure;
  expectedTitle: string;
}[] = [
  {
    name: '403',
    failure: { status: 403, code: 'ACCESS_DENIED' },
    expectedTitle: 'No tienes permiso para esta operación',
  },
  {
    name: '409',
    failure: { status: 409, code: 'CONFLICT' },
    expectedTitle: 'La operación entra en conflicto',
  },
];

for (const scenario of safeFailures) {
  test(`${scenario.name} is announced as a safe API error without retrying the mutation`, async ({
    page,
  }) => {
    const api = await installMockInventoryApi(page, { loginFailure: scenario.failure });
    await page.goto('/login');
    await login(page);

    const alert = page.locator('[data-error-source="api"][role="alert"]');
    await expect(alert).toHaveAccessibleName(scenario.expectedTitle);
    await expect(alert).toContainText(scenario.expectedTitle);
    await expect(page.locator('[data-error-source="routing"]')).toHaveCount(0);
    await expect(alert).not.toContainText('Internal variable detail');
    await expect(alert).not.toContainText('password=never-render-this');
    await expect(page.getByRole('button', { name: 'Reintentar' })).toHaveCount(0);
    expect(api.loginRequests()).toBe(1);
  });
}

test('429 respects Retry-After and does not resubmit while blocked', async ({ page }) => {
  await page.clock.install();
  const api = await installMockInventoryApi(page, {
    loginFailure: {
      status: 429,
      code: 'RATE_LIMIT_EXCEEDED',
      retryAfterSeconds: 30,
    },
  });
  await page.goto('/login');
  await login(page);

  const submit = page.locator('form button[type="submit"]');
  await expect(page.getByRole('alert')).toContainText('Espera antes de volver a intentarlo');
  await expect(submit).toBeDisabled();
  await expect(submit).toContainText('Disponible en');
  expect(api.loginRequests()).toBe(1);
  await page.clock.runFor(30_000);
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled();
  expect(api.loginRequests()).toBe(1);
});

test('correlation ID is labelled, selectable and copiable for support', async ({ page }) => {
  const correlationId = 'e2e-correlation-409';
  await installMockInventoryApi(page, {
    loginFailure: {
      status: 409,
      code: 'CONFLICT',
      correlationId,
    },
  });
  await page.goto('/login');
  await login(page);

  const reference = page.getByLabel('Referencia para soporte');
  await expect(reference).toHaveValue(correlationId);
  await expect(reference).toHaveAttribute('readonly', '');
  await page.getByRole('button', { name: 'Copiar referencia' }).click();
  await expect(page.getByRole('status')).toContainText('Referencia copiada.');
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(correlationId);
});
