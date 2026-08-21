import { Page, Route } from '@playwright/test';

export const API_ORIGIN = 'http://localhost:8080';

export type InventoryRole = 'ADMIN' | 'INVENTORY_MANAGER' | 'SALES';

export interface ApiFailure {
  readonly status: 403 | 409 | 429;
  readonly code: 'ACCESS_DENIED' | 'CONFLICT' | 'RATE_LIMIT_EXCEEDED';
  readonly correlationId?: string;
  readonly retryAfterSeconds?: number;
}

export interface MockInventoryApiOptions {
  readonly loginFailure?: ApiFailure;
  readonly rejectRefresh?: boolean;
  readonly expireFirstMeRequest?: boolean;
  readonly logoutStatus?: number;
}

export interface MockInventoryApiState {
  readonly loginRequests: () => number;
  readonly refreshRequests: () => number;
  readonly logoutRequests: () => number;
  readonly authHeaders: () => readonly (string | undefined)[];
}

const identifierRoles: Readonly<Record<string, InventoryRole>> = {
  admin: 'ADMIN',
  inventory: 'INVENTORY_MANAGER',
  sales: 'SALES',
};

export async function installMockInventoryApi(
  page: Page,
  options: MockInventoryApiOptions = {},
): Promise<MockInventoryApiState> {
  let currentRole: InventoryRole = 'ADMIN';
  let loginRequests = 0;
  let meRequests = 0;
  let refreshRequests = 0;
  let logoutRequests = 0;
  const authHeaders: (string | undefined)[] = [];

  await page.route(`${API_ORIGIN}/api/v1/**`, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    authHeaders.push(request.headers()['authorization']);

    switch (path) {
      case '/api/v1/auth/login': {
        loginRequests += 1;
        if (options.loginFailure) {
          await fulfillFailure(route, options.loginFailure);
          return;
        }

        const body = request.postDataJSON() as { identifier?: string };
        currentRole = identifierRoles[body.identifier ?? ''] ?? 'ADMIN';
        await fulfillJson(route, 200, tokenPair('initial'));
        return;
      }
      case '/api/v1/auth/me': {
        meRequests += 1;
        if (options.expireFirstMeRequest && meRequests === 1) {
          await fulfillJson(route, 401, { code: 'AUTHENTICATION_REQUIRED' });
          return;
        }
        await fulfillJson(route, 200, currentUser(currentRole));
        return;
      }
      case '/api/v1/auth/refresh': {
        refreshRequests += 1;
        if (options.rejectRefresh) {
          await fulfillJson(route, 401, { code: 'AUTHENTICATION_REQUIRED' });
          return;
        }
        await fulfillJson(route, 200, tokenPair('refreshed'));
        return;
      }
      case '/api/v1/auth/logout': {
        logoutRequests += 1;
        await route.fulfill({ status: options.logoutStatus ?? 204 });
        return;
      }
      default:
        await fulfillJson(route, 404, { code: 'RESOURCE_NOT_FOUND' });
    }
  });

  return {
    loginRequests: () => loginRequests,
    refreshRequests: () => refreshRequests,
    logoutRequests: () => logoutRequests,
    authHeaders: () => authHeaders,
  };
}

export async function login(page: Page, identifier = 'admin'): Promise<void> {
  await page.getByLabel('Usuario o correo').fill(identifier);
  await page.getByLabel('Contraseña').fill('E2E-password-only');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
}

async function fulfillJson(route: Route, status: number, body: unknown): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

async function fulfillFailure(route: Route, failure: ApiFailure): Promise<void> {
  await route.fulfill({
    status: failure.status,
    contentType: 'application/json',
    headers: {
      'Access-Control-Expose-Headers': 'Retry-After, X-Correlation-ID',
      ...(failure.correlationId ? { 'X-Correlation-ID': failure.correlationId } : {}),
      ...(failure.retryAfterSeconds !== undefined
        ? { 'Retry-After': String(failure.retryAfterSeconds) }
        : {}),
    },
    body: JSON.stringify({
      code: failure.code,
      message: 'Internal variable detail with password=never-render-this',
      correlationId: failure.correlationId,
    }),
  });
}

function tokenPair(generation: 'initial' | 'refreshed'): Readonly<Record<string, string>> {
  return {
    tokenType: 'Bearer',
    accessToken: `e2e-access-${generation}`,
    accessTokenExpiresAt: '2099-01-01T00:05:00Z',
    refreshToken: `e2e-refresh-${generation}`,
    refreshTokenExpiresAt: '2099-01-02T00:00:00Z',
  };
}

function currentUser(role: InventoryRole): Readonly<Record<string, unknown>> {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    username: role.toLowerCase(),
    email: `${role.toLowerCase()}@example.test`,
    roles: [role],
  };
}
