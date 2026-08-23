import { Page, Route } from '@playwright/test';

import { API_ORIGIN, installMockInventoryApi, MockInventoryApiOptions } from './mock-inventory-api';

interface MockProduct {
  readonly id: string;
  readonly sku: string;
  readonly name: string;
  readonly description?: string;
  readonly price: number;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface MockProductsApiState {
  readonly createBodies: () => readonly Readonly<Record<string, unknown>>[];
  readonly updateBodies: () => readonly Readonly<Record<string, unknown>>[];
  readonly deleteRequests: () => number;
}

export interface MockProductsBehavior {
  readonly deleteConflict?: {
    readonly message: string;
    readonly correlationId: string;
  };
  readonly deleteDelayMs?: number;
}

export async function installMockProductsApi(
  page: Page,
  authOptions: MockInventoryApiOptions = {},
  behavior: MockProductsBehavior = {},
): Promise<MockProductsApiState> {
  await installMockInventoryApi(page, authOptions);
  let products = initialProducts();
  const createBodies: Readonly<Record<string, unknown>>[] = [];
  const updateBodies: Readonly<Record<string, unknown>>[] = [];
  let deleteRequests = 0;

  await page.route(`${API_ORIGIN}/api/v1/products**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathParts = url.pathname.split('/').filter(Boolean);
    const id = pathParts.length === 4 ? pathParts[3] : undefined;

    if (request.method() === 'GET' && !id) {
      const sku = url.searchParams.get('sku')?.toLocaleLowerCase('es-MX');
      const name = url.searchParams.get('name')?.toLocaleLowerCase('es-MX');
      const active = url.searchParams.get('active');
      const pageNumber = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      const filtered = products.filter(
        (product) =>
          (!sku || product.sku.toLocaleLowerCase('es-MX').includes(sku)) &&
          (!name || product.name.toLocaleLowerCase('es-MX').includes(name)) &&
          (active === null || product.active === (active === 'true')),
      );
      const content = filtered.slice(pageNumber * size, (pageNumber + 1) * size);
      const totalPages = Math.ceil(filtered.length / size);
      await fulfillJson(route, 200, {
        content,
        page: pageNumber,
        size,
        totalElements: filtered.length,
        totalPages,
        first: pageNumber === 0,
        last: totalPages === 0 || pageNumber >= totalPages - 1,
      });
      return;
    }

    if (request.method() === 'GET' && id) {
      const product = products.find((candidate) => candidate.id === id);
      await fulfillJson(
        route,
        product ? 200 : 404,
        product ?? { code: 'RESOURCE_NOT_FOUND', correlationId: 'product-not-found-e2e' },
      );
      return;
    }

    if (request.method() === 'POST' && !id) {
      const body = request.postDataJSON() as Readonly<Record<string, unknown>>;
      createBodies.push(body);
      if (products.some((product) => product.sku === body['sku'])) {
        await fulfillJson(route, 409, {
          code: 'CONFLICT',
          message: 'Internal duplicate constraint detail must stay hidden',
          correlationId: 'duplicate-product-e2e',
        });
        return;
      }
      const product: MockProduct = {
        id: `product-${products.length + 1}`,
        sku: String(body['sku']),
        name: String(body['name']),
        ...(body['description'] ? { description: String(body['description']) } : {}),
        price: Number(body['price']),
        active: Boolean(body['active']),
        createdAt: '2026-08-21T12:00:00Z',
        updatedAt: '2026-08-21T12:00:00Z',
      };
      products = [...products, product];
      await fulfillJson(route, 201, product);
      return;
    }

    if (request.method() === 'PUT' && id) {
      const body = request.postDataJSON() as Readonly<Record<string, unknown>>;
      updateBodies.push(body);
      const existing = products.find((candidate) => candidate.id === id);
      if (!existing) {
        await fulfillJson(route, 404, { code: 'RESOURCE_NOT_FOUND' });
        return;
      }
      const updated: MockProduct = {
        ...existing,
        sku: String(body['sku']),
        name: String(body['name']),
        ...(body['description'] ? { description: String(body['description']) } : {}),
        price: Number(body['price']),
        active: Boolean(body['active']),
        updatedAt: '2026-08-21T12:30:00Z',
      };
      products = products.map((candidate) => (candidate.id === id ? updated : candidate));
      await fulfillJson(route, 200, updated);
      return;
    }

    if (request.method() === 'DELETE' && id) {
      deleteRequests += 1;
      if (behavior.deleteDelayMs) {
        await new Promise((resolve) => setTimeout(resolve, behavior.deleteDelayMs));
      }
      if (behavior.deleteConflict) {
        await fulfillJson(route, 409, {
          code: 'CONFLICT',
          message: behavior.deleteConflict.message,
          correlationId: behavior.deleteConflict.correlationId,
        });
        return;
      }
      products = products.filter((candidate) => candidate.id !== id);
      await route.fulfill({ status: 204 });
      return;
    }

    await fulfillJson(route, 405, { code: 'METHOD_NOT_ALLOWED' });
  });

  return {
    createBodies: () => createBodies,
    updateBodies: () => updateBodies,
    deleteRequests: () => deleteRequests,
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

function initialProducts(): MockProduct[] {
  return Array.from({ length: 25 }, (_, index) => {
    const sequence = index + 1;
    return {
      id: `product-${sequence}`,
      sku: `SKU-${String(sequence).padStart(3, '0')}`,
      name: `Producto ${String(sequence).padStart(2, '0')}`,
      description: `Descripción del producto ${sequence}`,
      price: 10 + sequence,
      active: sequence % 5 !== 0,
      createdAt: '2026-08-20T12:00:00Z',
      updatedAt: '2026-08-20T12:00:00Z',
    };
  });
}
