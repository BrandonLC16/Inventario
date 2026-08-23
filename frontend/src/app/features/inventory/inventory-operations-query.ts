import { ParamMap, Params } from '@angular/router';

import {
  FindAllMovementsRequestParams,
  FindLowStock1RequestParams,
} from '../../core/api/generated/api/inventory.service';

export const DEFAULT_OPERATIONS_PAGE_SIZE = 20;

export type MovementType = NonNullable<FindAllMovementsRequestParams['type']>;

export const MOVEMENT_TYPES: readonly MovementType[] = [
  'INITIAL_STOCK',
  'MANUAL_IN',
  'MANUAL_OUT',
  'ORDER_RESERVED',
  'ORDER_RESERVATION_RELEASED',
  'ORDER_CONFIRMED',
  'ORDER_CANCELLED',
  'PURCHASE_RECEIVED',
  'TRANSFER_OUT',
  'TRANSFER_IN',
  'PHYSICAL_COUNT_ADJUSTMENT',
];

export interface InventoryAlertsQuery {
  readonly page: number;
  readonly size: number;
  readonly search?: string;
  readonly outOfStockOnly: boolean;
}

export interface InventoryKardexQuery {
  readonly page: number;
  readonly size: number;
  readonly productId?: string;
  readonly type?: MovementType;
  readonly from?: string;
  readonly to?: string;
  readonly reference?: string;
}

export function inventoryAlertsQuery(paramMap: ParamMap): InventoryAlertsQuery {
  const search = cleanText(paramMap.get('search'));
  return {
    page: nonNegativeInteger(paramMap.get('page'), 0),
    size: positiveInteger(paramMap.get('size'), DEFAULT_OPERATIONS_PAGE_SIZE),
    ...(search ? { search } : {}),
    outOfStockOnly: paramMap.get('outOfStockOnly') === 'true',
  };
}

export function inventoryAlertsApiRequest(query: InventoryAlertsQuery): FindLowStock1RequestParams {
  return {
    page: query.page,
    size: query.size,
    ...(query.search ? { search: query.search } : {}),
    outOfStockOnly: query.outOfStockOnly,
  };
}

export function inventoryAlertsQueryParams(query: InventoryAlertsQuery): Params {
  return {
    ...(query.page > 0 ? { page: query.page } : {}),
    ...(query.size !== DEFAULT_OPERATIONS_PAGE_SIZE ? { size: query.size } : {}),
    ...(query.search ? { search: query.search } : {}),
    ...(query.outOfStockOnly ? { outOfStockOnly: true } : {}),
  };
}

export function sameInventoryAlertsQuery(
  left: InventoryAlertsQuery,
  right: InventoryAlertsQuery,
): boolean {
  return (
    left.page === right.page &&
    left.size === right.size &&
    left.search === right.search &&
    left.outOfStockOnly === right.outOfStockOnly
  );
}

export function inventoryKardexQuery(paramMap: ParamMap): InventoryKardexQuery {
  const productId = uuid(paramMap.get('productId'));
  const type = movementType(paramMap.get('type'));
  const from = instant(paramMap.get('from'));
  const to = instant(paramMap.get('to'));
  const reference = cleanText(paramMap.get('reference'));
  return {
    page: nonNegativeInteger(paramMap.get('page'), 0),
    size: positiveInteger(paramMap.get('size'), DEFAULT_OPERATIONS_PAGE_SIZE),
    ...(productId ? { productId } : {}),
    ...(type ? { type } : {}),
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
    ...(reference ? { reference } : {}),
  };
}

export function inventoryKardexApiRequest(
  query: InventoryKardexQuery,
): FindAllMovementsRequestParams {
  return {
    page: query.page,
    size: query.size,
    ...(query.productId ? { productId: query.productId } : {}),
    ...(query.type ? { type: query.type } : {}),
    ...(query.from ? { from: query.from } : {}),
    ...(query.to ? { to: query.to } : {}),
    ...(query.reference ? { reference: query.reference } : {}),
  };
}

export function inventoryKardexQueryParams(query: InventoryKardexQuery): Params {
  return {
    ...(query.page > 0 ? { page: query.page } : {}),
    ...(query.size !== DEFAULT_OPERATIONS_PAGE_SIZE ? { size: query.size } : {}),
    ...(query.productId ? { productId: query.productId } : {}),
    ...(query.type ? { type: query.type } : {}),
    ...(query.from ? { from: query.from } : {}),
    ...(query.to ? { to: query.to } : {}),
    ...(query.reference ? { reference: query.reference } : {}),
  };
}

export function sameInventoryKardexQuery(
  left: InventoryKardexQuery,
  right: InventoryKardexQuery,
): boolean {
  return (
    left.page === right.page &&
    left.size === right.size &&
    left.productId === right.productId &&
    left.type === right.type &&
    left.from === right.from &&
    left.to === right.to &&
    left.reference === right.reference
  );
}

export function localDateTimeToInstant(value: string): string | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = new Date(value);
  return Number.isFinite(parsed.getTime()) ? parsed.toISOString() : undefined;
}

export function instantToLocalDateTime(value: string | undefined): string {
  if (!value) {
    return '';
  }
  const parsed = new Date(value);
  if (!Number.isFinite(parsed.getTime())) {
    return '';
  }
  const local = new Date(parsed.getTime() - parsed.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function cleanText(value: string | null): string | undefined {
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
}

function uuid(value: string | null): string | undefined {
  const normalized = cleanText(value);
  return normalized &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(normalized)
    ? normalized
    : undefined;
}

function movementType(value: string | null): MovementType | undefined {
  return value && MOVEMENT_TYPES.some((candidate) => candidate === value)
    ? (value as MovementType)
    : undefined;
}

function instant(value: string | null): string | undefined {
  const normalized = cleanText(value);
  if (!normalized) {
    return undefined;
  }
  const parsed = new Date(normalized);
  return Number.isFinite(parsed.getTime()) ? parsed.toISOString() : undefined;
}

function nonNegativeInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function positiveInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 && parsed <= 100 ? parsed : fallback;
}
