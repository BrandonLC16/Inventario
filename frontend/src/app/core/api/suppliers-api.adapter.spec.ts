import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Configuration } from './generated/configuration';
import { PageResponseSupplierResponse } from './generated/model/page-response-supplier-response';
import { SupplierRequest } from './generated/model/supplier-request';
import { SupplierResponse } from './generated/model/supplier-response';
import { SuppliersApiAdapter } from './suppliers-api.adapter';

describe('SuppliersApiAdapter', () => {
  let adapter: SuppliersApiAdapter;
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
    adapter = TestBed.inject(SuppliersApiAdapter);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('delegates combined server-side filters and pagination to the generated client', () => {
    const response: PageResponseSupplierResponse = {
      content: [{ id: 'supplier-1', code: 'SUP-01', legalName: 'Proveedor' }],
      page: 2,
      size: 25,
      totalElements: 1,
      totalPages: 3,
      first: false,
      last: true,
    };
    let received: PageResponseSupplierResponse | undefined;

    adapter
      .list({
        page: 2,
        size: 25,
        code: 'SUP',
        name: 'Proveedor',
        fiscalIdentifier: 'RFC',
        active: true,
      })
      .subscribe((value) => (received = value));

    const request = httpTesting.expectOne(
      (candidate) => candidate.url === 'https://api.example.test/api/v1/suppliers',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().sort()).toEqual(
      ['active', 'code', 'fiscalIdentifier', 'name', 'page', 'size'].sort(),
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('active')).toBe('true');
    expect(request.request.withCredentials).toBe(false);
    request.flush(response);

    expect(received).toEqual(response);
  });

  it('delegates detail, create and update with generated types', () => {
    const id = '4f9575b3-6500-4df0-88af-e9a94e1b7b6b';
    const body: SupplierRequest = {
      code: 'SUP-01',
      legalName: 'Proveedor Uno',
      fiscalIdentifier: 'RFC010101AA1',
      email: 'compras@example.test',
      active: true,
    };
    const response: SupplierResponse = { id, ...body };

    adapter.get(id).subscribe();
    const detailRequest = httpTesting.expectOne(`https://api.example.test/api/v1/suppliers/${id}`);
    expect(detailRequest.request.method).toBe('GET');
    detailRequest.flush(response);

    adapter.create(body).subscribe();
    const createRequest = httpTesting.expectOne('https://api.example.test/api/v1/suppliers');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual(body);
    createRequest.flush(response);

    adapter.update(id, { ...body, active: false }).subscribe();
    const updateRequest = httpTesting.expectOne(`https://api.example.test/api/v1/suppliers/${id}`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual({ ...body, active: false });
    updateRequest.flush({ ...response, active: false });
  });

  it('only reconciles deactivation when the generated operation returns 204', () => {
    const id = '4f9575b3-6500-4df0-88af-e9a94e1b7b6b';
    let completed = false;
    adapter.deactivate(id).subscribe(() => (completed = true));
    const success = httpTesting.expectOne(`https://api.example.test/api/v1/suppliers/${id}`);
    expect(success.request.method).toBe('DELETE');
    success.flush(null, { status: 204, statusText: 'No Content' });
    expect(completed).toBe(true);

    let failure: unknown;
    adapter.deactivate(id).subscribe({ error: (error: unknown) => (failure = error) });
    const unexpected = httpTesting.expectOne(`https://api.example.test/api/v1/suppliers/${id}`);
    unexpected.flush({}, { status: 200, statusText: 'OK' });
    expect(failure).toEqual(new Error('Unexpected supplier deactivation status: 200'));
  });
});
