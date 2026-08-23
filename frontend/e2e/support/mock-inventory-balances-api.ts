import { Page, Route } from '@playwright/test';

import { API_ORIGIN, installMockInventoryApi, MockInventoryApiState } from './mock-inventory-api';

const MAIN_WAREHOUSE_ID = '00000000-0000-0000-0000-000000000001';
export const NORTH_WAREHOUSE_ID = '00000000-0000-0000-0000-000000000002';

interface MockWarehouse {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly active: boolean;
}

interface MockProduct {
  readonly id: string;
  readonly sku: string;
  readonly name: string;
}

export interface MockInventoryBalancesState {
  readonly balanceRequests: () => readonly string[];
  readonly settingsRequests: () => readonly string[];
}

export type AdjustmentFailure = 'network' | 400 | 401 | 403 | 429;

export interface MockInventoryBalancesOptions {
  readonly adjustmentFailure?: AdjustmentFailure;
}

export interface MockAdjustmentRequest {
  readonly path: string;
  readonly body: { readonly quantityDelta: number; readonly reference?: string };
}

export interface MockInventoryAdjustmentsState
  extends MockInventoryBalancesState, MockInventoryApiState {
  readonly adjustmentRequests: () => readonly MockAdjustmentRequest[];
}

export async function installMockInventoryBalancesApi(
  page: Page,
  options: MockInventoryBalancesOptions = {},
): Promise<MockInventoryAdjustmentsState> {
  const auth = await installMockInventoryApi(page);
  const warehouses: readonly MockWarehouse[] = [
    { id: MAIN_WAREHOUSE_ID, code: 'MAIN', name: 'Almacén principal', active: true },
    { id: NORTH_WAREHOUSE_ID, code: 'NORTH', name: 'Almacén norte', active: true },
  ];
  const products = initialProducts();
  const balanceRequests: string[] = [];
  const settingsRequests: string[] = [];
  const adjustmentRequests: MockAdjustmentRequest[] = [];
  const adjustedBalances = new Map<string, number>();

  await page.route(`${API_ORIGIN}/api/v1/inventory**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const parts = url.pathname.split('/').filter(Boolean);
    if (request.method() === 'GET' && url.pathname === '/api/v1/inventory') {
      balanceRequests.push(`${url.pathname}${url.search}`);
      await fulfillBalancePage(route, url, products, MAIN_WAREHOUSE_ID, adjustedBalances);
      return;
    }
    if (request.method() === 'PATCH' && parts.length === 5 && parts[4] === 'adjustments') {
      await fulfillAdjustment(
        route,
        url.pathname,
        MAIN_WAREHOUSE_ID,
        parts[3] ?? '',
        adjustmentRequests,
        adjustedBalances,
        options.adjustmentFailure,
      );
      return;
    }
    await fulfillJson(route, 405, { code: 'METHOD_NOT_ALLOWED' });
  });

  await page.route(`${API_ORIGIN}/api/v1/warehouses**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const parts = url.pathname.split('/').filter(Boolean);

    if (request.method() === 'GET' && parts.length === 3) {
      const pageNumber = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      await fulfillJson(route, 200, pageResponse(warehouses, pageNumber, size));
      return;
    }

    const warehouseId = parts[3] ?? '';
    if (request.method() === 'GET' && parts.length === 4) {
      const warehouse = warehouses.find((candidate) => candidate.id === warehouseId);
      await fulfillJson(
        route,
        warehouse ? 200 : 404,
        warehouse ?? { code: 'RESOURCE_NOT_FOUND', correlationId: 'inventory-warehouse-404' },
      );
      return;
    }

    if (request.method() === 'GET' && parts[4] === 'inventory' && parts.length === 5) {
      balanceRequests.push(`${url.pathname}${url.search}`);
      await fulfillBalancePage(route, url, products, warehouseId, adjustedBalances);
      return;
    }

    if (
      request.method() === 'PATCH' &&
      parts[4] === 'inventory' &&
      parts[6] === 'adjustments' &&
      parts.length === 7
    ) {
      await fulfillAdjustment(
        route,
        url.pathname,
        warehouseId,
        parts[5] ?? '',
        adjustmentRequests,
        adjustedBalances,
        options.adjustmentFailure,
      );
      return;
    }

    if (
      request.method() === 'GET' &&
      parts[4] === 'inventory' &&
      parts[5] === 'settings' &&
      parts.length === 6
    ) {
      settingsRequests.push(`${url.pathname}${url.search}`);
      const pageNumber = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      const response = pageResponse(products, pageNumber, size);
      await fulfillJson(route, 200, {
        ...response,
        content: [...response.content].reverse().map((product) => ({
          warehouseId,
          productId: product.id,
          sku: product.sku,
          name: product.name,
          minimumStock: 0,
          active: true,
        })),
      });
      return;
    }

    await fulfillJson(route, 405, { code: 'METHOD_NOT_ALLOWED' });
  });

  return {
    ...auth,
    balanceRequests: () => balanceRequests,
    settingsRequests: () => settingsRequests,
    adjustmentRequests: () => adjustmentRequests,
  };
}

async function fulfillBalancePage(
  route: Route,
  url: URL,
  products: readonly MockProduct[],
  warehouseId: string,
  adjustedBalances: ReadonlyMap<string, number>,
): Promise<void> {
  const pageNumber = Number(url.searchParams.get('page') ?? 0);
  const size = Number(url.searchParams.get('size') ?? 20);
  const response = pageResponse(products, pageNumber, size);
  await fulfillJson(route, 200, {
    ...response,
    content: response.content.map((product, index) => {
      const sequence = pageNumber * size + index + 1;
      const initialQuantity = warehouseId === MAIN_WAREHOUSE_ID ? sequence - 1 : 100 + sequence;
      const quantity = adjustedBalances.get(`${warehouseId}:${product.id}`) ?? initialQuantity;
      const reservedQuantity = sequence % 2 === 0 ? 1 : 0;
      return {
        warehouseId,
        productId: product.id,
        quantity,
        reservedQuantity,
        availableQuantity: quantity - reservedQuantity,
        ...(quantity === 0 ? {} : { updatedAt: '2026-08-23T12:00:00Z' }),
      };
    }),
  });
}

async function fulfillAdjustment(
  route: Route,
  path: string,
  warehouseId: string,
  productId: string,
  requests: MockAdjustmentRequest[],
  adjustedBalances: Map<string, number>,
  failure?: AdjustmentFailure,
): Promise<void> {
  const body = route.request().postDataJSON() as MockAdjustmentRequest['body'];
  requests.push({ path, body });
  if (failure === 'network') {
    await route.abort('failed');
    return;
  }
  if (failure) {
    await route.fulfill({
      status: failure,
      contentType: 'application/json',
      headers:
        failure === 429
          ? {
              'Access-Control-Expose-Headers': 'Retry-After, X-Correlation-ID',
              'Retry-After': '30',
              'X-Correlation-ID': 'adjust-429',
            }
          : {},
      body: JSON.stringify({
        code:
          failure === 400
            ? 'INVALID_REQUEST'
            : failure === 401
              ? 'AUTHENTICATION_REQUIRED'
              : failure === 403
                ? 'ACCESS_DENIED'
                : 'RATE_LIMIT_EXCEEDED',
        correlationId: `adjust-${failure}`,
      }),
    });
    return;
  }

  const key = `${warehouseId}:${productId}`;
  const sequence = Number(productId.slice(-12));
  const initialQuantity = warehouseId === MAIN_WAREHOUSE_ID ? sequence - 1 : 100 + sequence;
  const quantity = (adjustedBalances.get(key) ?? initialQuantity) + body.quantityDelta;
  const reservedQuantity = sequence % 2 === 0 ? 1 : 0;
  adjustedBalances.set(key, quantity);
  await fulfillJson(route, 200, {
    warehouseId,
    productId,
    quantity,
    reservedQuantity,
    availableQuantity: quantity - reservedQuantity,
    updatedAt: '2026-08-23T13:00:00Z',
  });
}

function pageResponse<T>(content: readonly T[], page: number, size: number) {
  const pageContent = content.slice(page * size, (page + 1) * size);
  const totalPages = Math.ceil(content.length / size);
  return {
    content: pageContent,
    page,
    size,
    totalElements: content.length,
    totalPages,
    first: page === 0,
    last: totalPages === 0 || page >= totalPages - 1,
  };
}

async function fulfillJson(route: Route, status: number, body: unknown): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

function initialProducts(): readonly MockProduct[] {
  return Array.from({ length: 25 }, (_, index) => {
    const sequence = index + 1;
    return {
      id: `10000000-0000-0000-0000-${String(sequence).padStart(12, '0')}`,
      sku: `SKU-${String(sequence).padStart(3, '0')}`,
      name: `Producto ${String(sequence).padStart(2, '0')}`,
    };
  });
}
