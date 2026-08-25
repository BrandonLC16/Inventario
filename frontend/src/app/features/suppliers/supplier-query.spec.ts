import { convertToParamMap } from '@angular/router';

import { supplierApiRequest, supplierListQuery, supplierQueryParams } from './supplier-query';

describe('supplier list query', () => {
  it('normalizes combined filters, preserves valid pagination and rejects invalid values', () => {
    const query = supplierListQuery(
      convertToParamMap({
        page: '2',
        size: '25',
        code: ' SUP ',
        name: ' Demo ',
        fiscalIdentifier: ' RFC ',
        active: 'false',
      }),
    );

    expect(query).toEqual({
      page: 2,
      size: 25,
      code: 'SUP',
      name: 'Demo',
      fiscalIdentifier: 'RFC',
      active: false,
    });
    expect(supplierApiRequest(query)).toEqual(query);
    expect(supplierQueryParams(query)).toEqual({
      page: 2,
      size: 25,
      code: 'SUP',
      name: 'Demo',
      fiscalIdentifier: 'RFC',
      active: false,
    });

    expect(
      supplierListQuery(convertToParamMap({ page: '-1', size: '101', active: 'other' })),
    ).toEqual({ page: 0, size: 20 });
  });
});
