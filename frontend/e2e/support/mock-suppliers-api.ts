import { Page, Route } from '@playwright/test';

import { API_ORIGIN, installMockInventoryApi, MockInventoryApiOptions } from './mock-inventory-api';

interface MockSupplier {
  readonly id: string;
  readonly code: string;
  readonly legalName: string;
  readonly commercialName?: string;
  readonly fiscalIdentifier?: string;
  readonly email?: string;
  readonly phone?: string;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface MockSuppliersApiState {
  readonly createBodies: () => readonly Readonly<Record<string, unknown>>[];
  readonly updateBodies: () => readonly Readonly<Record<string, unknown>>[];
  readonly deactivateRequests: () => number;
  readonly listRequests: () => number;
}

export interface MockSuppliersBehavior {
  readonly conflictField?: 'code' | 'fiscalIdentifier' | 'email';
  readonly createDelayMs?: number;
  readonly deactivateDelayMs?: number;
}

export async function installMockSuppliersApi(
  page: Page,
  authOptions: MockInventoryApiOptions = {},
  behavior: MockSuppliersBehavior = {},
): Promise<MockSuppliersApiState> {
  await installMockInventoryApi(page, authOptions);
  let suppliers = initialSuppliers();
  const createBodies: Readonly<Record<string, unknown>>[] = [];
  const updateBodies: Readonly<Record<string, unknown>>[] = [];
  let deactivateRequests = 0;
  let listRequests = 0;

  await page.route(`${API_ORIGIN}/api/v1/suppliers**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathParts = url.pathname.split('/').filter(Boolean);
    const id = pathParts.length === 4 ? pathParts[3] : undefined;

    if (request.method() === 'GET' && !id) {
      listRequests += 1;
      const code = url.searchParams.get('code')?.toLocaleLowerCase('es-MX');
      const name = url.searchParams.get('name')?.toLocaleLowerCase('es-MX');
      const fiscalIdentifier = url.searchParams.get('fiscalIdentifier')?.toLocaleLowerCase('es-MX');
      const active = url.searchParams.get('active');
      const pageNumber = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      const filtered = suppliers.filter(
        (supplier) =>
          (!code || supplier.code.toLocaleLowerCase('es-MX').includes(code)) &&
          (!name ||
            supplier.legalName.toLocaleLowerCase('es-MX').includes(name) ||
            supplier.commercialName?.toLocaleLowerCase('es-MX').includes(name)) &&
          (!fiscalIdentifier ||
            supplier.fiscalIdentifier?.toLocaleLowerCase('es-MX').includes(fiscalIdentifier)) &&
          (active === null || supplier.active === (active === 'true')),
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
      const supplier = suppliers.find((candidate) => candidate.id === id);
      await fulfillJson(
        route,
        supplier ? 200 : 404,
        supplier ?? { code: 'RESOURCE_NOT_FOUND', correlationId: 'supplier-not-found-e2e' },
      );
      return;
    }

    if (request.method() === 'POST' && !id) {
      const body = request.postDataJSON() as Readonly<Record<string, unknown>>;
      createBodies.push(body);
      if (behavior.createDelayMs) {
        await new Promise((resolve) => setTimeout(resolve, behavior.createDelayMs));
      }
      if (behavior.conflictField) {
        await fulfillJson(route, 409, {
          code: 'CONFLICT',
          message: `Internal duplicate ${behavior.conflictField} detail must stay hidden`,
          correlationId: `duplicate-supplier-${behavior.conflictField}`,
        });
        return;
      }
      const supplier = normalizeSupplier(body, `supplier-${suppliers.length + 1}`);
      suppliers = [...suppliers, supplier];
      await fulfillJson(route, 201, supplier);
      return;
    }

    if (request.method() === 'PUT' && id) {
      const body = request.postDataJSON() as Readonly<Record<string, unknown>>;
      updateBodies.push(body);
      const existing = suppliers.find((candidate) => candidate.id === id);
      if (!existing) {
        await fulfillJson(route, 404, { code: 'RESOURCE_NOT_FOUND' });
        return;
      }
      const updated = normalizeSupplier(body, id, existing.createdAt);
      suppliers = suppliers.map((candidate) => (candidate.id === id ? updated : candidate));
      await fulfillJson(route, 200, updated);
      return;
    }

    if (request.method() === 'DELETE' && id) {
      deactivateRequests += 1;
      if (behavior.deactivateDelayMs) {
        await new Promise((resolve) => setTimeout(resolve, behavior.deactivateDelayMs));
      }
      suppliers = suppliers.map((supplier) =>
        supplier.id === id
          ? { ...supplier, active: false, updatedAt: '2026-08-21T13:00:00Z' }
          : supplier,
      );
      await route.fulfill({ status: 204 });
      return;
    }

    await fulfillJson(route, 405, { code: 'METHOD_NOT_ALLOWED' });
  });

  return {
    createBodies: () => createBodies,
    updateBodies: () => updateBodies,
    deactivateRequests: () => deactivateRequests,
    listRequests: () => listRequests,
  };
}

function normalizeSupplier(
  body: Readonly<Record<string, unknown>>,
  id: string,
  createdAt = '2026-08-21T12:00:00Z',
): MockSupplier {
  const optional = (field: string): string | undefined => {
    const value = String(body[field] ?? '').trim();
    return value || undefined;
  };
  const commercialName = optional('commercialName');
  const fiscalIdentifier = optional('fiscalIdentifier')?.toUpperCase();
  const email = optional('email')?.toLowerCase();
  const phone = optional('phone');
  return {
    id,
    code: String(body['code']).trim().toUpperCase(),
    legalName: String(body['legalName']).trim(),
    ...(commercialName ? { commercialName } : {}),
    ...(fiscalIdentifier ? { fiscalIdentifier } : {}),
    ...(email ? { email } : {}),
    ...(phone ? { phone } : {}),
    active: Boolean(body['active']),
    createdAt,
    updatedAt: '2026-08-21T12:30:00Z',
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

function initialSuppliers(): MockSupplier[] {
  return Array.from({ length: 25 }, (_, index) => {
    const sequence = index + 1;
    const padded = String(sequence).padStart(3, '0');
    return {
      id: `supplier-${sequence}`,
      code: `SUP-${padded}`,
      legalName: `Proveedor ${padded}, S.A. de C.V.`,
      commercialName: `Comercial ${padded}`,
      fiscalIdentifier: `RFC${padded}0101AA1`,
      email: `compras${sequence}@example.test`,
      phone: `+52 55 0000 ${String(sequence).padStart(4, '0')}`,
      active: sequence % 5 !== 0,
      createdAt: '2026-08-20T12:00:00Z',
      updatedAt: '2026-08-20T12:00:00Z',
    };
  });
}
