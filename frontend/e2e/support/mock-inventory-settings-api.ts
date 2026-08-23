import { Page, Route } from '@playwright/test';

import { API_ORIGIN, installMockInventoryApi } from './mock-inventory-api';

export const SETTINGS_NORTH_WAREHOUSE_ID = '00000000-0000-0000-0000-000000000012';
export const SETTINGS_SOUTH_WAREHOUSE_ID = '00000000-0000-0000-0000-000000000013';

interface MockInventorySettingsOptions {
  readonly conflictOnDeactivate?: boolean;
}

interface MockProduct {
  readonly id: string;
  readonly sku: string;
  readonly name: string;
  readonly active: boolean;
}

interface MockSetting {
  minimumStock: number;
  active: boolean;
}

export interface MockInventorySettingsState {
  readonly listRequests: () => readonly string[];
  readonly configureRequests: () => readonly Readonly<Record<string, unknown>>[];
  readonly settingDetailRequests: () => number;
}

export async function installMockInventorySettingsApi(
  page: Page,
  options: MockInventorySettingsOptions = {},
): Promise<MockInventorySettingsState> {
  await installMockInventoryApi(page);
  const products = initialProducts();
  const settings = new Map<string, Map<string, MockSetting>>([
    [
      SETTINGS_NORTH_WAREHOUSE_ID,
      new Map(
        products.map((product, index) => [
          product.id,
          { minimumStock: index, active: index % 3 !== 1 },
        ]),
      ),
    ],
    [
      SETTINGS_SOUTH_WAREHOUSE_ID,
      new Map(
        products.map((product, index) => [
          product.id,
          { minimumStock: 100 + index, active: index % 2 === 0 },
        ]),
      ),
    ],
  ]);
  const listRequests: string[] = [];
  const configureRequests: Readonly<Record<string, unknown>>[] = [];
  let settingDetailRequests = 0;

  await page.route(`${API_ORIGIN}/api/v1/products/**`, async (route) => {
    const request = route.request();
    const productId = new URL(request.url()).pathname.split('/').filter(Boolean).at(-1) ?? '';
    const product = products.find((candidate) => candidate.id === productId);
    if (request.method() === 'GET' && product) {
      await fulfillJson(route, 200, { ...product, price: 10 });
      return;
    }
    await fulfillJson(route, 404, {
      code: 'RESOURCE_NOT_FOUND',
      correlationId: 'settings-product-404',
    });
  });

  await page.route(`${API_ORIGIN}/api/v1/warehouses/**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const parts = url.pathname.split('/').filter(Boolean);
    const warehouseId = parts[3] ?? '';
    const warehouse = warehouseResponse(warehouseId);

    if (request.method() === 'GET' && parts.length === 4) {
      await fulfillJson(
        route,
        warehouse ? 200 : 404,
        warehouse ?? { code: 'RESOURCE_NOT_FOUND', correlationId: 'settings-warehouse-404' },
      );
      return;
    }

    if (
      request.method() === 'GET' &&
      parts[4] === 'inventory' &&
      parts[5] === 'settings' &&
      parts.length === 6
    ) {
      listRequests.push(`${url.pathname}${url.search}`);
      const pageNumber = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      const pageProducts = products.slice(pageNumber * size, (pageNumber + 1) * size);
      await fulfillJson(route, 200, {
        content: pageProducts.map((product) => settingResponse(warehouseId, product, settings)),
        page: pageNumber,
        size,
        totalElements: products.length,
        totalPages: Math.ceil(products.length / size),
        first: pageNumber === 0,
        last: (pageNumber + 1) * size >= products.length,
      });
      return;
    }

    if (parts[4] === 'inventory' && parts[6] === 'settings' && parts.length === 7) {
      const productId = parts[5] ?? '';
      const product = products.find((candidate) => candidate.id === productId);
      const warehouseSettings = settings.get(warehouseId);
      if (!product || !warehouseSettings?.has(productId)) {
        await fulfillJson(route, 404, {
          code: 'RESOURCE_NOT_FOUND',
          correlationId: 'settings-detail-404',
        });
        return;
      }

      if (request.method() === 'GET') {
        settingDetailRequests += 1;
        await fulfillJson(route, 200, settingResponse(warehouseId, product, settings));
        return;
      }

      if (request.method() === 'PUT') {
        const body = request.postDataJSON() as { minimumStock?: number; active?: boolean };
        configureRequests.push({ warehouseId, productId, ...body });
        if (options.conflictOnDeactivate && body.active === false) {
          await route.fulfill({
            status: 409,
            contentType: 'application/json',
            headers: {
              'Access-Control-Expose-Headers': 'X-Correlation-ID',
              'X-Correlation-ID': 'settings-stock-conflict',
            },
            body: JSON.stringify({ code: 'CONFLICT' }),
          });
          return;
        }
        warehouseSettings.set(productId, {
          minimumStock: Number(body.minimumStock),
          active: body.active === true,
        });
        await new Promise((resolve) => setTimeout(resolve, 100));
        await route.fulfill({ status: 204 });
        return;
      }
    }

    await fulfillJson(route, 405, { code: 'METHOD_NOT_ALLOWED' });
  });

  return {
    listRequests: () => listRequests,
    configureRequests: () => configureRequests,
    settingDetailRequests: () => settingDetailRequests,
  };
}

function warehouseResponse(id: string): Readonly<Record<string, unknown>> | undefined {
  if (id === SETTINGS_NORTH_WAREHOUSE_ID) {
    return { id, code: 'NORTH', name: 'Almacén norte', active: true };
  }
  if (id === SETTINGS_SOUTH_WAREHOUSE_ID) {
    return { id, code: 'SOUTH', name: 'Almacén sur', active: true };
  }
  return undefined;
}

function settingResponse(
  warehouseId: string,
  product: MockProduct,
  settings: ReadonlyMap<string, ReadonlyMap<string, MockSetting>>,
): Readonly<Record<string, unknown>> {
  const setting = settings.get(warehouseId)?.get(product.id);
  return {
    warehouseId,
    productId: product.id,
    sku: product.sku,
    name: product.name,
    minimumStock: setting?.minimumStock ?? 0,
    active: setting?.active ?? false,
  };
}

function initialProducts(): readonly MockProduct[] {
  return Array.from({ length: 25 }, (_, index) => {
    const sequence = index + 1;
    return {
      id: `20000000-0000-0000-0000-${String(sequence).padStart(12, '0')}`,
      sku: `SETTING-${String(sequence).padStart(3, '0')}`,
      name: `Producto configurable ${String(sequence).padStart(2, '0')}`,
      active: index !== 0,
    };
  });
}

async function fulfillJson(route: Route, status: number, body: unknown): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}
