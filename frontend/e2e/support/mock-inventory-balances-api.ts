import { Page, Route } from '@playwright/test';

import { API_ORIGIN, installMockInventoryApi } from './mock-inventory-api';

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

export async function installMockInventoryBalancesApi(
  page: Page,
): Promise<MockInventoryBalancesState> {
  await installMockInventoryApi(page);
  const warehouses: readonly MockWarehouse[] = [
    { id: MAIN_WAREHOUSE_ID, code: 'MAIN', name: 'Almacén principal', active: true },
    { id: NORTH_WAREHOUSE_ID, code: 'NORTH', name: 'Almacén norte', active: true },
  ];
  const products = initialProducts();
  const balanceRequests: string[] = [];
  const settingsRequests: string[] = [];

  await page.route(`${API_ORIGIN}/api/v1/inventory**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'GET' && url.pathname === '/api/v1/inventory') {
      balanceRequests.push(`${url.pathname}${url.search}`);
      await fulfillBalancePage(route, url, products, MAIN_WAREHOUSE_ID);
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
      await fulfillBalancePage(route, url, products, warehouseId);
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
    balanceRequests: () => balanceRequests,
    settingsRequests: () => settingsRequests,
  };
}

async function fulfillBalancePage(
  route: Route,
  url: URL,
  products: readonly MockProduct[],
  warehouseId: string,
): Promise<void> {
  const pageNumber = Number(url.searchParams.get('page') ?? 0);
  const size = Number(url.searchParams.get('size') ?? 20);
  const response = pageResponse(products, pageNumber, size);
  await fulfillJson(route, 200, {
    ...response,
    content: response.content.map((product, index) => {
      const sequence = pageNumber * size + index + 1;
      const quantity = warehouseId === MAIN_WAREHOUSE_ID ? sequence - 1 : 100 + sequence;
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
