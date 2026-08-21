import { convertToParamMap } from '@angular/router';

import { productApiRequest, productListQuery, productQueryParams } from './product-query';

describe('product list query', () => {
  it('normalizes remote filters and rejects invalid pagination', () => {
    const query = productListQuery(
      convertToParamMap({ page: '-1', size: '999', sku: ' SKU ', name: ' Demo ', active: 'false' }),
    );

    expect(query).toEqual({ page: 0, size: 20, sku: 'SKU', name: 'Demo', active: false });
    expect(productApiRequest(query)).toEqual(query);
    expect(productQueryParams(query)).toEqual({ sku: 'SKU', name: 'Demo', active: false });
  });
});
