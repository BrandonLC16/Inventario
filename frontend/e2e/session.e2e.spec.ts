import { expect, Locator, Page, test } from '@playwright/test';

import { installMockInventoryApi, InventoryRole, login } from './support/mock-inventory-api';

const commonNavigation = ['dashboard', 'products', 'warehouses', 'inventory', 'profile'];
const roleScenarios: readonly {
  role: InventoryRole;
  identifier: string;
  visible: readonly string[];
  forbiddenPath?: string;
}[] = [
  {
    role: 'ADMIN',
    identifier: 'admin',
    visible: [
      ...commonNavigation,
      'suppliers',
      'purchases',
      'transfers',
      'inventory-counts',
      'customers',
      'orders',
      'users',
    ],
  },
  {
    role: 'INVENTORY_MANAGER',
    identifier: 'inventory',
    visible: [...commonNavigation, 'suppliers', 'purchases', 'transfers', 'inventory-counts'],
    forbiddenPath: '/admin/users',
  },
  {
    role: 'SALES',
    identifier: 'sales',
    visible: [...commonNavigation, 'customers', 'orders'],
    forbiddenPath: '/suppliers',
  },
];

test('visitor is redirected to login with an internal return URL', async ({ page }) => {
  await installMockInventoryApi(page);
  await page.goto('/orders');

  await expect(page).toHaveURL(/\/login\?returnUrl=%2Forders$/);
  await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
});

test('valid login loads /me and keeps the session out of browser persistence', async ({
  context,
  page,
}) => {
  const api = await installMockInventoryApi(page);
  await page.goto('/login');
  await login(page);

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Resumen' })).toBeFocused();
  expect(api.loginRequests()).toBe(1);
  expect(api.authHeaders()[0]).toBeUndefined();
  expect(api.authHeaders()).toContain('Bearer e2e-access-initial');

  const persisted = await page.evaluate(async () => ({
    localStorage: Object.entries(localStorage),
    sessionStorage: Object.entries(sessionStorage),
    indexedDb: await indexedDB.databases(),
    cacheNames: await caches.keys(),
    serviceWorkers: (await navigator.serviceWorker.getRegistrations()).length,
  }));
  expect(persisted).toEqual({
    localStorage: [],
    sessionStorage: [],
    indexedDb: [],
    cacheNames: [],
    serviceWorkers: 0,
  });
  expect(await context.cookies()).toEqual([]);
});

for (const scenario of roleScenarios) {
  test(`${scenario.role} sees only its menu and route policy`, async ({ page }) => {
    await installMockInventoryApi(page);
    await page.goto('/login');
    await login(page, scenario.identifier);
    await expect(page).toHaveURL(/\/dashboard$/);

    const visibleIds = await page.locator('[data-nav-id]').evaluateAll((links) =>
      links
        .map((link) => link.getAttribute('data-nav-id'))
        .filter((value): value is string => value !== null)
        .sort(),
    );
    expect(visibleIds).toEqual([...scenario.visible].sort());

    if (scenario.forbiddenPath) {
      await navigateWithinSpa(page, scenario.forbiddenPath);
      await expect(page).toHaveURL(/\/forbidden$/);
      await expect(page.locator('[data-error-source="routing"]')).toContainText('403');
    } else {
      await page.locator('[data-nav-id="users"]').click();
      await expect(page).toHaveURL(/\/admin\/users$/);
      await expect(page.getByRole('heading', { name: 'Usuarios' })).toBeVisible();
    }
  });
}

test('reload loses the memory-only session and asks for login again', async ({ page }) => {
  await installMockInventoryApi(page);
  await page.goto('/login');
  await login(page);
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.reload();

  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fdashboard$/);
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible();
});

test('rejected refresh clears the partial login without starting an auth loop', async ({
  page,
}) => {
  const api = await installMockInventoryApi(page, {
    expireFirstMeRequest: true,
    rejectRefresh: true,
  });
  await page.goto('/login');
  await login(page);

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('alert')).toContainText('La sesión ya no está disponible');
  expect(api.loginRequests()).toBe(1);
  expect(api.refreshRequests()).toBe(1);
});

test('degraded logout still clears memory and returns to login', async ({ page }) => {
  const api = await installMockInventoryApi(page, { logoutStatus: 503 });
  await page.goto('/login');
  await login(page);
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();

  await expect(page).toHaveURL(/\/login$/);
  expect(api.logoutRequests()).toBe(1);
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fdashboard$/);
});

test('login and primary navigation work with keyboard only', async ({ page }) => {
  await installMockInventoryApi(page);
  await page.goto('/login');

  const identifier = page.getByLabel('Usuario o correo');
  await focusWithTab(page, identifier);
  await page.keyboard.type('admin');
  const password = page.getByLabel('Contraseña');
  await focusWithTab(page, password);
  await page.keyboard.type('E2E-password-only');
  await page.keyboard.press('Enter');

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Resumen' })).toBeFocused();
  await page.getByRole('link', { name: 'Productos' }).focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/products$/);
  await expect(page.getByRole('heading', { name: 'Productos' })).toBeFocused();
});

test('mobile menu fits the viewport and restores focus after Escape', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installMockInventoryApi(page);
  await page.goto('/login');
  await login(page, 'sales');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Resumen' })).toBeFocused();

  const menuButton = page.getByRole('button', { name: 'Abrir o cerrar navegación' });
  await expect(menuButton).toBeVisible();
  await menuButton.focus();
  await page.keyboard.press('Enter');
  await expect(menuButton).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('link', { name: 'Resumen', exact: true })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(menuButton).toHaveAttribute('aria-expanded', 'false');
  await expect(menuButton).toBeFocused();

  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
});

async function navigateWithinSpa(page: Page, path: string): Promise<void> {
  await page.evaluate((destination) => {
    window.history.pushState({}, '', destination);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}

async function focusWithTab(page: Page, target: Locator): Promise<void> {
  for (let attempt = 0; attempt < 6; attempt += 1) {
    if (await target.evaluate((element) => element === document.activeElement)) {
      return;
    }
    await page.keyboard.press('Tab');
  }
  await expect(target).toBeFocused();
}
