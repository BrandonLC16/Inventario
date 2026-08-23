import { Page, Route } from '@playwright/test';

import { API_ORIGIN, installMockInventoryApi, MockInventoryApiOptions } from './mock-inventory-api';

interface MockWarehouse {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly description?: string;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface MockWarehousesApiState {
  readonly listQueries: () => readonly string[];
  readonly createBodies: () => readonly Readonly<Record<string, unknown>>[];
  readonly updateBodies: () => readonly Readonly<Record<string, unknown>>[];
  readonly deactivateRequests: () => number;
}

export async function installMockWarehousesApi(
  page: Page,
  authOptions: MockInventoryApiOptions = {},
): Promise<MockWarehousesApiState> {
  await installMockInventoryApi(page, authOptions);
  let warehouses = initialWarehouses();
  const listQueries: string[] = [];
  const createBodies: Readonly<Record<string, unknown>>[] = [];
  const updateBodies: Readonly<Record<string, unknown>>[] = [];
  let deactivateRequests = 0;

  await page.route(`${API_ORIGIN}/api/v1/warehouses**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathParts = url.pathname.split('/').filter(Boolean);
    const id = pathParts.length === 4 ? pathParts[3] : undefined;

    if (request.method() === 'GET' && !id) {
      listQueries.push(url.search);
      const pageNumber = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      const content = warehouses.slice(pageNumber * size, (pageNumber + 1) * size);
      const totalPages = Math.ceil(warehouses.length / size);
      await fulfillJson(route, 200, {
        content,
        page: pageNumber,
        size,
        totalElements: warehouses.length,
        totalPages,
        first: pageNumber === 0,
        last: totalPages === 0 || pageNumber >= totalPages - 1,
      });
      return;
    }

    if (request.method() === 'GET' && id) {
      const warehouse = warehouses.find((candidate) => candidate.id === id);
      await fulfillJson(
        route,
        warehouse ? 200 : 404,
        warehouse ?? { code: 'RESOURCE_NOT_FOUND', correlationId: 'warehouse-not-found-e2e' },
      );
      return;
    }

    if (request.method() === 'POST' && !id) {
      const body = request.postDataJSON() as Readonly<Record<string, unknown>>;
      createBodies.push(body);
      const normalizedCode = String(body['code']).trim().toUpperCase();
      if (warehouses.some((warehouse) => warehouse.code === normalizedCode)) {
        await fulfillJson(route, 409, {
          code: 'CONFLICT',
          message: 'Internal duplicate warehouse constraint must stay hidden',
          correlationId: 'duplicate-warehouse-e2e',
        });
        return;
      }
      const warehouse: MockWarehouse = {
        id: `warehouse-${warehouses.length + 1}`,
        code: normalizedCode,
        name: String(body['name']),
        ...(body['description'] ? { description: String(body['description']) } : {}),
        active: Boolean(body['active']),
        createdAt: '2026-08-23T12:00:00Z',
        updatedAt: '2026-08-23T12:00:00Z',
      };
      warehouses = [...warehouses, warehouse];
      await fulfillJson(route, 201, warehouse);
      return;
    }

    if (request.method() === 'PUT' && id) {
      const body = request.postDataJSON() as Readonly<Record<string, unknown>>;
      updateBodies.push(body);
      const existing = warehouses.find((candidate) => candidate.id === id);
      if (!existing) {
        await fulfillJson(route, 404, { code: 'RESOURCE_NOT_FOUND' });
        return;
      }
      const normalizedCode = String(body['code']).trim().toUpperCase();
      if (
        warehouses.some((warehouse) => warehouse.id !== id && warehouse.code === normalizedCode)
      ) {
        await fulfillJson(route, 409, {
          code: 'CONFLICT',
          correlationId: 'duplicate-warehouse-e2e',
        });
        return;
      }
      const updated: MockWarehouse = {
        ...existing,
        code: normalizedCode,
        name: String(body['name']),
        ...(body['description'] ? { description: String(body['description']) } : {}),
        active: Boolean(body['active']),
        updatedAt: '2026-08-23T12:30:00Z',
      };
      warehouses = warehouses.map((candidate) => (candidate.id === id ? updated : candidate));
      await fulfillJson(route, 200, updated);
      return;
    }

    if (request.method() === 'DELETE' && id) {
      deactivateRequests += 1;
      if (id === 'warehouse-1') {
        await fulfillJson(route, 409, {
          code: 'CONFLICT',
          message: 'Internal stock balance must stay hidden',
          correlationId: 'warehouse-stock-conflict-e2e',
        });
        return;
      }
      warehouses = warehouses.map((candidate) =>
        candidate.id === id
          ? { ...candidate, active: false, updatedAt: '2026-08-23T13:00:00Z' }
          : candidate,
      );
      await route.fulfill({ status: 204 });
      return;
    }

    await fulfillJson(route, 405, { code: 'METHOD_NOT_ALLOWED' });
  });

  return {
    listQueries: () => listQueries,
    createBodies: () => createBodies,
    updateBodies: () => updateBodies,
    deactivateRequests: () => deactivateRequests,
  };
}

async function fulfillJson(route: Route, status: number, body: unknown): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    headers: { 'Access-Control-Expose-Headers': 'X-Correlation-ID' },
    body: JSON.stringify(body),
  });
}

function initialWarehouses(): MockWarehouse[] {
  return Array.from({ length: 25 }, (_, index) => {
    const sequence = index + 1;
    return {
      id: `warehouse-${sequence}`,
      code: sequence === 1 ? 'MAIN' : `WH-${String(sequence).padStart(2, '0')}`,
      name: sequence === 1 ? 'Almacén principal' : `Almacén ${String(sequence).padStart(2, '0')}`,
      description: `Ubicación operativa ${sequence}`,
      active: sequence % 5 !== 0,
      createdAt: '2026-08-20T12:00:00Z',
      updatedAt: '2026-08-20T12:00:00Z',
    };
  });
}
