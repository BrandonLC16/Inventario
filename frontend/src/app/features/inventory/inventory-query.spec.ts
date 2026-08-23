import { convertToParamMap } from '@angular/router';

import { inventoryListQuery, inventoryQueryParams } from './inventory-query';

describe('inventory query', () => {
  it('normalizes balance and warehouse-selector pagination independently', () => {
    expect(
      inventoryListQuery(
        convertToParamMap({ page: '-1', size: '999', warehousePage: '3', search: 'ignored' }),
      ),
    ).toEqual({ page: 0, size: 20, warehousePage: 3 });
  });

  it('keeps non-default remote pages navigable', () => {
    expect(inventoryQueryParams({ page: 2, size: 25, warehousePage: 4 })).toEqual({
      page: 2,
      size: 25,
      warehousePage: 4,
    });
  });
});
