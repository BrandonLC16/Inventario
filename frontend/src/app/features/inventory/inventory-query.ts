import { ParamMap, Params } from '@angular/router';

export const DEFAULT_INVENTORY_PAGE_SIZE = 20;
export const DEFAULT_WAREHOUSE_SELECTOR_PAGE_SIZE = 20;

export interface InventoryListQuery {
  readonly page: number;
  readonly size: number;
  readonly warehousePage: number;
}

export function inventoryListQuery(paramMap: ParamMap): InventoryListQuery {
  return {
    page: nonNegativeInteger(paramMap.get('page'), 0),
    size: positiveInteger(paramMap.get('size'), DEFAULT_INVENTORY_PAGE_SIZE),
    warehousePage: nonNegativeInteger(paramMap.get('warehousePage'), 0),
  };
}

export function inventoryQueryParams(query: InventoryListQuery): Params {
  return {
    ...(query.page > 0 ? { page: query.page } : {}),
    ...(query.size !== DEFAULT_INVENTORY_PAGE_SIZE ? { size: query.size } : {}),
    ...(query.warehousePage > 0 ? { warehousePage: query.warehousePage } : {}),
  };
}

function nonNegativeInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

function positiveInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 && parsed <= 100 ? parsed : fallback;
}
