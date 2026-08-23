import { convertToParamMap } from '@angular/router';

import {
  inventoryAlertsQuery,
  inventoryAlertsQueryParams,
  inventoryKardexQuery,
  inventoryKardexQueryParams,
  localDateTimeToInstant,
} from './inventory-operations-query';

describe('inventory operations query', () => {
  it('normalizes alert filters and remote pagination', () => {
    expect(
      inventoryAlertsQuery(
        convertToParamMap({
          page: '-1',
          size: '101',
          search: '  tornillo  ',
          outOfStockOnly: 'true',
        }),
      ),
    ).toEqual({ page: 0, size: 20, search: 'tornillo', outOfStockOnly: true });
    expect(
      inventoryAlertsQueryParams({ page: 2, size: 25, search: 'SKU-1', outOfStockOnly: true }),
    ).toEqual({ page: 2, size: 25, search: 'SKU-1', outOfStockOnly: true });
  });

  it('keeps only contract-valid Kardex filters and serializes them to the URL', () => {
    const query = inventoryKardexQuery(
      convertToParamMap({
        page: '1',
        productId: '10000000-0000-4000-8000-000000000001',
        type: 'ORDER_RESERVED',
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-02T12:30:00Z',
        reference: ' ORDER-1 ',
      }),
    );
    expect(query).toMatchObject({
      page: 1,
      productId: '10000000-0000-4000-8000-000000000001',
      type: 'ORDER_RESERVED',
      from: '2026-08-01T00:00:00.000Z',
      to: '2026-08-02T12:30:00.000Z',
      reference: 'ORDER-1',
    });
    expect(inventoryKardexQueryParams(query)).toMatchObject({ page: 1, type: 'ORDER_RESERVED' });
  });

  it('drops invalid enum, UUID and date values before an API request', () => {
    expect(
      inventoryKardexQuery(
        convertToParamMap({ productId: 'deleted-product', type: 'UNKNOWN', from: 'not-a-date' }),
      ),
    ).toEqual({ page: 0, size: 20 });
    expect(localDateTimeToInstant('not-a-date')).toBeUndefined();
  });
});
