import { convertToParamMap } from '@angular/router';

import {
  DEFAULT_INVENTORY_SETTINGS_PAGE_SIZE,
  inventorySettingsQuery,
  inventorySettingsQueryParams,
} from './inventory-settings-query';

describe('inventory settings query', () => {
  it('normalizes unsafe pagination values', () => {
    expect(inventorySettingsQuery(convertToParamMap({ page: '-1', size: '101' }))).toEqual({
      page: 0,
      size: DEFAULT_INVENTORY_SETTINGS_PAGE_SIZE,
    });
  });

  it('keeps valid remote pagination and omits defaults from the URL', () => {
    expect(inventorySettingsQuery(convertToParamMap({ page: '2', size: '25' }))).toEqual({
      page: 2,
      size: 25,
    });
    expect(inventorySettingsQueryParams({ page: 0, size: 20 })).toEqual({});
    expect(inventorySettingsQueryParams({ page: 2, size: 25 })).toEqual({ page: 2, size: 25 });
  });
});
