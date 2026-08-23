import { ParamMap, Params } from '@angular/router';

export const DEFAULT_INVENTORY_SETTINGS_PAGE_SIZE = 20;

export interface InventorySettingsQuery {
  readonly page: number;
  readonly size: number;
}

export function inventorySettingsQuery(paramMap: ParamMap): InventorySettingsQuery {
  return {
    page: nonNegativeInteger(paramMap.get('page'), 0),
    size: positiveInteger(paramMap.get('size'), DEFAULT_INVENTORY_SETTINGS_PAGE_SIZE),
  };
}

export function inventorySettingsQueryParams(query: InventorySettingsQuery): Params {
  return {
    ...(query.page > 0 ? { page: query.page } : {}),
    ...(query.size !== DEFAULT_INVENTORY_SETTINGS_PAGE_SIZE ? { size: query.size } : {}),
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
