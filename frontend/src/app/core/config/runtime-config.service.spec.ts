import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { RuntimeConfigService } from './runtime-config.service';

describe('RuntimeConfigService', () => {
  let service: RuntimeConfigService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RuntimeConfigService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads and normalizes the API origin without credentials', async () => {
    const loading = service.load(TestBed.inject(HttpClient));
    const request = httpTesting.expectOne('config/runtime-config.json');

    expect(request.request.headers.get('Accept')).toBe('application/json');
    request.flush({ apiBaseUrl: 'http://localhost:8080/' });
    await loading;

    expect(service.apiBaseUrl).toBe('http://localhost:8080');
  });

  it('rejects configuration containing credentials or an API path', async () => {
    const loading = service.load(TestBed.inject(HttpClient));
    httpTesting
      .expectOne('config/runtime-config.json')
      .flush({ apiBaseUrl: 'https://user:password@example.test/api' });

    await expect(loading).rejects.toThrow(
      'apiBaseUrl must be an HTTP(S) origin without credentials, path or query.',
    );
  });
});
