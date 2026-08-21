import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Configuration } from './generated/configuration';
import { PageResponseProductResponse } from './generated/model/page-response-product-response';
import { ProductRequest } from './generated/model/product-request';
import { ProductResponse } from './generated/model/product-response';
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

  it('delegates detail, create, update and delete to generated operations', () => {
    const id = 'f6a621b4-37a2-4952-b5ef-2809ea628f54';
    const body: ProductRequest = {
      sku: 'SKU-01',
      name: 'Demo',
      price: 15.5,
      active: true,
      minimumStock: 4,
    };
    const response: ProductResponse = { id, ...body };

    adapter.get(id).subscribe();
    const detailRequest = httpTesting.expectOne(`https://api.example.test/api/v1/products/${id}`);
    expect(detailRequest.request.method).toBe('GET');
    detailRequest.flush(response);

    adapter.create(body).subscribe();
    const createRequest = httpTesting.expectOne('https://api.example.test/api/v1/products');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(body);
    createRequest.flush(response);

    const updateBody: ProductRequest = { sku: 'SKU-01', name: 'Edited', price: 16, active: false };
    adapter.update(id, updateBody).subscribe();
    const updateRequest = httpTesting.expectOne(`https://api.example.test/api/v1/products/${id}`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual(updateBody);
    expect(updateRequest.request.body).not.toHaveProperty('minimumStock');
    updateRequest.flush({ ...response, ...updateBody });

    adapter.delete(id).subscribe();
    const deleteRequest = httpTesting.expectOne(`https://api.example.test/api/v1/products/${id}`);
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null, { status: 204, statusText: 'No Content' });
  });
});
