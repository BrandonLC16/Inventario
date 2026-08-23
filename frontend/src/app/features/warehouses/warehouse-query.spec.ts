import { convertToParamMap } from '@angular/router';

import { warehouseApiRequest, warehouseListQuery, warehouseQueryParams } from './warehouse-query';

describe('warehouse list query', () => {
  it('normalizes pagination and never forwards unsupported search parameters', () => {
    const query = warehouseListQuery(
      convertToParamMap({ page: '-1', size: '999', search: 'MAIN', active: 'true' }),
    );

    expect(query).toEqual({ page: 0, size: 20 });
    expect(warehouseApiRequest(query)).toEqual({ page: 0, size: 20 });
    expect(warehouseQueryParams(query)).toEqual({});
  });

  it('keeps a valid remote page and size navigable', () => {
    const query = warehouseListQuery(convertToParamMap({ page: '3', size: '25' }));

    expect(warehouseApiRequest(query)).toEqual({ page: 3, size: 25 });
    expect(warehouseQueryParams(query)).toEqual({ page: 3, size: 25 });
  });
});
