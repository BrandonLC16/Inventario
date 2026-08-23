import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Configuration } from './generated/configuration';
import { PageResponseWarehouseResponse } from './generated/model/page-response-warehouse-response';
import { WarehouseRequest } from './generated/model/warehouse-request';
import { WarehouseResponse } from './generated/model/warehouse-response';
import { WarehousesApiAdapter } from './warehouses-api.adapter';

describe('WarehousesApiAdapter', () => {
  let adapter: WarehousesApiAdapter;
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

    adapter = TestBed.inject(WarehousesApiAdapter);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('uses only server-side page and size with the configured API origin', () => {
    const response: PageResponseWarehouseResponse = {
      content: [{ id: 'warehouse-1', code: 'MAIN', name: 'Principal', active: true }],
      page: 2,
      size: 25,
      totalElements: 1,
      totalPages: 1,
      first: false,
      last: true,
    };
    let received: PageResponseWarehouseResponse | undefined;

    adapter.list({ page: 2, size: 25 }).subscribe((value) => {
      received = value;
    });

    const request = httpTesting.expectOne(
      (candidate) => candidate.url === 'https://api.example.test/api/v1/warehouses',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.withCredentials).toBe(false);

    request.flush(response);
    expect(received).toEqual(response);
  });

  it('delegates detail, create, update and deactivate to generated operations', () => {
    const id = 'f6a621b4-37a2-4952-b5ef-2809ea628f54';
    const body: WarehouseRequest = {
      code: 'NORTH',
      name: 'Norte',
      description: 'Centro norte',
      active: true,
    };
    const response: WarehouseResponse = { id, ...body };

    adapter.get(id).subscribe();
    const detailRequest = httpTesting.expectOne(`https://api.example.test/api/v1/warehouses/${id}`);
    expect(detailRequest.request.method).toBe('GET');
    detailRequest.flush(response);

    adapter.create(body).subscribe();
    const createRequest = httpTesting.expectOne('https://api.example.test/api/v1/warehouses');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(body);
    createRequest.flush(response);

    const updateBody: WarehouseRequest = { ...body, name: 'Norte actualizado' };
    adapter.update(id, updateBody).subscribe();
    const updateRequest = httpTesting.expectOne(`https://api.example.test/api/v1/warehouses/${id}`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual(updateBody);
    updateRequest.flush({ ...response, ...updateBody });

    adapter.deactivate(id).subscribe();
    const deactivateRequest = httpTesting.expectOne(
      `https://api.example.test/api/v1/warehouses/${id}`,
    );
    expect(deactivateRequest.request.method).toBe('DELETE');
    deactivateRequest.flush(null, { status: 204, statusText: 'No Content' });
  });
});
