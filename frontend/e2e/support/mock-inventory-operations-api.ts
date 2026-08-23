import { Page, Route } from '@playwright/test';

import { API_ORIGIN } from './mock-inventory-api';
import {
  installMockInventoryBalancesApi,
  MockInventoryAdjustmentsState,
  NORTH_WAREHOUSE_ID,
} from './mock-inventory-balances-api';

const MAIN_WAREHOUSE_ID = '00000000-0000-0000-0000-000000000001';
export const HISTORICAL_PRODUCT_ID = '90000000-0000-4000-8000-000000000099';

export interface MockInventoryOperationsOptions {
  readonly emptyAlerts?: boolean;
  readonly failMovements?: boolean;
}

export interface MockInventoryOperationsState extends MockInventoryAdjustmentsState {
  readonly alertRequests: () => readonly string[];
  readonly movementRequests: () => readonly string[];
  readonly productDetailRequests: () => number;
}

export async function installMockInventoryOperationsApi(
  page: Page,
  options: MockInventoryOperationsOptions = {},
): Promise<MockInventoryOperationsState> {
  const base = await installMockInventoryBalancesApi(page);
  const alertRequests: string[] = [];
  const movementRequests: string[] = [];
  let productDetailRequests = 0;

  await page.route(`${API_ORIGIN}/api/v1/products/**`, async (route) => {
    productDetailRequests += 1;
    await route.fallback();
  });

  await page.route(`${API_ORIGIN}/api/v1/inventory/**`, async (route) => {
    const url = new URL(route.request().url());
    if (route.request().method() === 'GET' && url.pathname === '/api/v1/inventory/low-stock') {
      alertRequests.push(`${url.pathname}${url.search}`);
      await fulfillAlerts(route, url, MAIN_WAREHOUSE_ID, options.emptyAlerts === true);
      return;
    }
    if (route.request().method() === 'GET' && url.pathname === '/api/v1/inventory/movements') {
      movementRequests.push(`${url.pathname}${url.search}`);
      await fulfillMovements(route, url, MAIN_WAREHOUSE_ID, options.failMovements === true);
      return;
    }
    await route.fallback();
  });

  await page.route(`${API_ORIGIN}/api/v1/warehouses/**`, async (route) => {
    const url = new URL(route.request().url());
    const parts = url.pathname.split('/').filter(Boolean);
    const warehouseId = parts[3] ?? '';
    if (
      route.request().method() === 'GET' &&
      parts[4] === 'inventory' &&
      parts[5] === 'low-stock' &&
      parts.length === 6
    ) {
      alertRequests.push(`${url.pathname}${url.search}`);
      await fulfillAlerts(route, url, warehouseId, options.emptyAlerts === true);
      return;
    }
    if (
      route.request().method() === 'GET' &&
      parts[4] === 'inventory' &&
      parts[5] === 'movements' &&
      parts.length === 6
    ) {
      movementRequests.push(`${url.pathname}${url.search}`);
      await fulfillMovements(route, url, warehouseId, options.failMovements === true);
      return;
    }
    await route.fallback();
  });

  return {
    ...base,
    alertRequests: () => alertRequests,
    movementRequests: () => movementRequests,
    productDetailRequests: () => productDetailRequests,
  };
}

async function fulfillAlerts(
  route: Route,
  url: URL,
  warehouseId: string,
  forceEmpty: boolean,
): Promise<void> {
  const page = Number(url.searchParams.get('page') ?? 0);
  const size = Number(url.searchParams.get('size') ?? 20);
  const search = url.searchParams.get('search')?.toLowerCase() ?? '';
  const outOfStockOnly = url.searchParams.get('outOfStockOnly') === 'true';
  const rows = forceEmpty || search === 'sin-resultados' ? [] : alerts(warehouseId);
  const filtered = rows.filter(
    (row) =>
      (!search ||
        row.sku.toLowerCase().includes(search) ||
        row.name.toLowerCase().includes(search)) &&
      (!outOfStockOnly || row.alert === 'OUT_OF_STOCK'),
  );
  await fulfillJson(route, 200, pageResponse(filtered, page, size));
}

async function fulfillMovements(
  route: Route,
  url: URL,
  warehouseId: string,
  fail: boolean,
): Promise<void> {
  if (fail) {
    await fulfillJson(route, 503, {
      code: 'INTERNAL_ERROR',
      correlationId: 'kardex-e2e-error',
    });
    return;
  }
  const page = Number(url.searchParams.get('page') ?? 0);
  const size = Number(url.searchParams.get('size') ?? 20);
  const productId = url.searchParams.get('productId');
  const type = url.searchParams.get('type');
  const reference = url.searchParams.get('reference');
  const rows = movements(warehouseId).filter(
    (row) =>
      (!productId || row.productId === productId) &&
      (!type || row.movementType === type) &&
      (!reference || row.businessReference === reference),
  );
  await fulfillJson(route, 200, pageResponse(rows, page, size));
}

function alerts(warehouseId: string) {
  return Array.from({ length: 45 }, (_, index) => {
    const sequence = index + 1;
    const out = sequence % 2 === 1;
    const availableQuantity = out ? 0 : 2;
    const minimumStock = 5;
    return {
      warehouseId,
      productId: `80000000-0000-4000-8000-${String(sequence).padStart(12, '0')}`,
      sku: `ALERT-${String(sequence).padStart(3, '0')}`,
      name: `Producto alerta ${sequence}`,
      quantity: availableQuantity + 1,
      reservedQuantity: 1,
      availableQuantity,
      minimumStock,
      replenishmentQuantity: minimumStock - availableQuantity,
      alert: out ? 'OUT_OF_STOCK' : 'LOW_STOCK',
    };
  });
}

function movements(warehouseId: string) {
  return [
    {
      id: '70000000-0000-4000-8000-000000000001',
      warehouseId,
      productId: HISTORICAL_PRODUCT_ID,
      movementType: 'ORDER_RESERVED',
      quantityDelta: 0,
      balanceBefore: 8,
      balanceAfter: 8,
      reservationDelta: 3,
      reservedBefore: 0,
      reservedAfter: 3,
      businessReference: 'ORDER-DELETED-1',
      occurredAt: '2026-08-20T12:00:00Z',
      responsibleUser: 'manager-history-id',
    },
    {
      id: '70000000-0000-4000-8000-000000000002',
      warehouseId,
      productId: '80000000-0000-4000-8000-000000000002',
      movementType: 'MANUAL_IN',
      quantityDelta: 4,
      balanceBefore: 2,
      balanceAfter: 6,
      reservationDelta: 0,
      reservedBefore: 0,
      reservedAfter: 0,
      businessReference: 'COUNT-2',
      occurredAt: '2026-08-19T12:00:00Z',
      responsibleUser: 'manager-current-id',
    },
  ];
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

export { NORTH_WAREHOUSE_ID };
