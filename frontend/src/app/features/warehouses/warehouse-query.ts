import { ParamMap, Params } from '@angular/router';

import { FindAllRequestParams } from '../../core/api/generated/api/warehouses.service';

export const DEFAULT_WAREHOUSE_PAGE_SIZE = 20;

export interface WarehouseListQuery {
  readonly page: number;
  readonly size: number;
}

export function warehouseListQuery(paramMap: ParamMap): WarehouseListQuery {
  return {
    page: nonNegativeInteger(paramMap.get('page'), 0),
    size: positiveInteger(paramMap.get('size'), DEFAULT_WAREHOUSE_PAGE_SIZE),
  };
}

export function warehouseApiRequest(query: WarehouseListQuery): FindAllRequestParams {
  return { ...query };
}

export function warehouseQueryParams(query: WarehouseListQuery): Params {
  return {
    ...(query.page > 0 ? { page: query.page } : {}),
    ...(query.size !== DEFAULT_WAREHOUSE_PAGE_SIZE ? { size: query.size } : {}),
  };
}

export function sameWarehouseQuery(left: WarehouseListQuery, right: WarehouseListQuery): boolean {
  return left.page === right.page && left.size === right.size;
}

function nonNegativeInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function positiveInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 && parsed <= 100 ? parsed : fallback;
}
