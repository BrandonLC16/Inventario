import { ParamMap, Params } from '@angular/router';

import { FindAll2RequestParams } from '../../core/api/generated/api/suppliers.service';

export const DEFAULT_SUPPLIER_PAGE_SIZE = 20;

export interface SupplierListQuery {
  readonly page: number;
  readonly size: number;
  readonly code?: string;
  readonly name?: string;
  readonly fiscalIdentifier?: string;
  readonly active?: boolean;
}

export function supplierListQuery(paramMap: ParamMap): SupplierListQuery {
  const code = cleanText(paramMap.get('code'));
  const name = cleanText(paramMap.get('name'));
  const fiscalIdentifier = cleanText(paramMap.get('fiscalIdentifier'));
  const activeValue = paramMap.get('active');

  return {
    page: nonNegativeInteger(paramMap.get('page'), 0),
    size: positiveInteger(paramMap.get('size'), DEFAULT_SUPPLIER_PAGE_SIZE),
    ...(code ? { code } : {}),
    ...(name ? { name } : {}),
    ...(fiscalIdentifier ? { fiscalIdentifier } : {}),
    ...(activeValue === 'true' || activeValue === 'false'
      ? { active: activeValue === 'true' }
      : {}),
  };
}

export function supplierApiRequest(query: SupplierListQuery): FindAll2RequestParams {
  return { ...query };
}

export function supplierQueryParams(query: SupplierListQuery): Params {
  return {
    ...(query.page > 0 ? { page: query.page } : {}),
    ...(query.size !== DEFAULT_SUPPLIER_PAGE_SIZE ? { size: query.size } : {}),
    ...(query.code ? { code: query.code } : {}),
    ...(query.name ? { name: query.name } : {}),
    ...(query.fiscalIdentifier ? { fiscalIdentifier: query.fiscalIdentifier } : {}),
    ...(query.active !== undefined ? { active: query.active } : {}),
  };
}

export function sameSupplierQuery(left: SupplierListQuery, right: SupplierListQuery): boolean {
  return (
    left.page === right.page &&
    left.size === right.size &&
    left.code === right.code &&
    left.name === right.name &&
    left.fiscalIdentifier === right.fiscalIdentifier &&
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
