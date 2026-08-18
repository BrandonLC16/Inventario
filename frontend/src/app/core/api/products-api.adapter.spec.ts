import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Configuration } from './generated/configuration';
import { PageResponseProductResponse } from './generated/model/page-response-product-response';
import { ProductsApiAdapter } from './products-api.adapter';

describe('ProductsApiAdapter', () => {
  let adapter: ProductsApiAdapter;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: Configuration,
          useValue: new Configuration({
            basePath: 'https://api.example.test',
            withCredentials: false,
          }),
        },
      ],
    });

    adapter = TestBed.inject(ProductsApiAdapter);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('uses the generated client with the configured origin and server-side filters', () => {
    const response: PageResponseProductResponse = {
      content: [{ id: 'f6a621b4-37a2-4952-b5ef-2809ea628f54', sku: 'SKU-01', name: 'Demo' }],
      page: 2,
      size: 25,
      totalElements: 1,
      totalPages: 1,
      first: false,
      last: true,
    };
    let received: PageResponseProductResponse | undefined;

    adapter.list({ page: 2, size: 25, active: true, sku: 'SKU' }).subscribe((value) => {
      received = value;
    });

    const request = httpTesting.expectOne(
      (candidate) => candidate.url === 'https://api.example.test/api/v1/products',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('active')).toBe('true');
    expect(request.request.params.get('sku')).toBe('SKU');
    expect(request.request.withCredentials).toBe(false);

    request.flush(response);
    expect(received).toEqual(response);
  });
});
