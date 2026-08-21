import { ParamMap, Params } from '@angular/router';

import { FindAll4RequestParams } from '../../core/api/generated/api/products.service';

export const DEFAULT_PRODUCT_PAGE_SIZE = 20;

export interface ProductListQuery {
  readonly page: number;
  readonly size: number;
  readonly sku?: string;
  readonly name?: string;
  readonly active?: boolean;
}

export function productListQuery(paramMap: ParamMap): ProductListQuery {
  const sku = cleanText(paramMap.get('sku'));
  const name = cleanText(paramMap.get('name'));
  const activeValue = paramMap.get('active');

  return {
    page: nonNegativeInteger(paramMap.get('page'), 0),
    size: positiveInteger(paramMap.get('size'), DEFAULT_PRODUCT_PAGE_SIZE),
    ...(sku ? { sku } : {}),
    ...(name ? { name } : {}),
    ...(activeValue === 'true' || activeValue === 'false'
      ? { active: activeValue === 'true' }
      : {}),
  };
}

export function productApiRequest(query: ProductListQuery): FindAll4RequestParams {
  return { ...query };
}

export function productQueryParams(query: ProductListQuery): Params {
  return {
    ...(query.page > 0 ? { page: query.page } : {}),
    ...(query.size !== DEFAULT_PRODUCT_PAGE_SIZE ? { size: query.size } : {}),
    ...(query.sku ? { sku: query.sku } : {}),
    ...(query.name ? { name: query.name } : {}),
    ...(query.active !== undefined ? { active: query.active } : {}),
  };
}

export function sameProductQuery(left: ProductListQuery, right: ProductListQuery): boolean {
  return (
    left.page === right.page &&
    left.size === right.size &&
    left.sku === right.sku &&
    left.name === right.name &&
    left.active === right.active
  );
}

function cleanText(value: string | null): string | undefined {
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
}

function nonNegativeInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function positiveInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 && parsed <= 100 ? parsed : fallback;
}
