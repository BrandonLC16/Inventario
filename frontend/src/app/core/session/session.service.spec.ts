import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { Configuration } from '../api/generated/configuration';
import { CurrentUserResponse } from '../api/generated/model/current-user-response';
import { TokenResponse } from '../api/generated/model/token-response';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { DISABLE_AUTH_REPLAY, sessionInterceptor } from './session.interceptor';
import { SessionService } from './session.service';

const API_ORIGIN = 'https://api.example.test';
const FIRST_PAIR: TokenResponse = {
  tokenType: 'Bearer',
  accessToken: 'access-first',
  accessTokenExpiresAt: '2026-08-21T12:05:00Z',
  refreshToken: 'refresh-first',
  refreshTokenExpiresAt: '2026-09-04T12:00:00Z',
};
const SECOND_PAIR: TokenResponse = {
  tokenType: 'Bearer',
  accessToken: 'access-second',
  accessTokenExpiresAt: '2026-08-21T12:10:00Z',
  refreshToken: 'refresh-second',
  refreshTokenExpiresAt: '2026-09-04T12:05:00Z',
};
const CURRENT_USER = {
  id: 'f6a621b4-37a2-4952-b5ef-2809ea628f54',
  username: 'alicia',
  email: 'alicia@example.test',
  roles: ['ADMIN'],
};

describe('SessionService and sessionInterceptor', () => {
  let session: SessionService;
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  const configureTestingModule = (): void => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([sessionInterceptor])),
        provideHttpClientTesting(),
        {
          provide: Configuration,
          useValue: new Configuration({ basePath: API_ORIGIN, withCredentials: false }),
        },
        {
          provide: RuntimeConfigService,
          useValue: {
            matchesApiOrigin: (url: string) =>
              new URL(url, 'https://app.example.test').origin === API_ORIGIN,
          },
        },
      ],
    });
    session = TestBed.inject(SessionService);
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  };

  beforeEach(configureTestingModule);

  afterEach(() => httpTesting.verify());

  function authenticate(pair: TokenResponse = FIRST_PAIR): void {
    let user: CurrentUserResponse | undefined;
    session.login({ identifier: 'alicia', password: 'correct-password' }).subscribe((value) => {
      user = value;
    });

    const loginRequest = httpTesting.expectOne(`${API_ORIGIN}/api/v1/auth/login`);
    expect(loginRequest.request.headers.has('Authorization')).toBe(false);
    expect(loginRequest.request.withCredentials).toBe(false);
    loginRequest.flush(pair);

    const meRequest = httpTesting.expectOne(`${API_ORIGIN}/api/v1/auth/me`);
    expect(meRequest.request.headers.get('Authorization')).toBe(`Bearer ${pair.accessToken}`);
    meRequest.flush(CURRENT_USER);

    expect(user?.username).toBe('alicia');
    expect(session.isAuthenticated()).toBe(true);
  }

  it('keeps login credentials only in the service instance and loads /me', () => {
    authenticate();

    expect(session.accessToken()).toBe('access-first');
    expect(session.roles()).toEqual(['ADMIN']);
    expect(session.user()?.email).toBe('alicia@example.test');
  });

  it('shares one refresh across concurrent 401 responses and retries both requests', () => {
    authenticate();
    const results: string[] = [];

    http.get(`${API_ORIGIN}/api/v1/products`).subscribe(() => results.push('products'));
    http.get(`${API_ORIGIN}/api/v1/warehouses`).subscribe(() => results.push('warehouses'));

    const initialRequests = httpTesting.match(
      (request) => request.url.endsWith('/products') || request.url.endsWith('/warehouses'),
    );
    expect(initialRequests).toHaveLength(2);
    expect(
      initialRequests.every(
        (request) => request.request.headers.get('Authorization') === 'Bearer access-first',
      ),
    ).toBe(true);
    initialRequests.forEach((request) =>
      request.flush(null, { status: 401, statusText: 'Unauthorized' }),
    );

    const refreshRequest = httpTesting.expectOne(`${API_ORIGIN}/api/v1/auth/refresh`);
    expect(refreshRequest.request.headers.has('Authorization')).toBe(false);
    expect(refreshRequest.request.body).toEqual({ refreshToken: 'refresh-first' });
    refreshRequest.flush(SECOND_PAIR);

    const retries = httpTesting.match(
      (request) => request.url.endsWith('/products') || request.url.endsWith('/warehouses'),
    );
    expect(retries).toHaveLength(2);
    expect(
      retries.every(
        (request) => request.request.headers.get('Authorization') === 'Bearer access-second',
      ),
    ).toBe(true);
    retries.forEach((request) => request.flush({}));

    expect(results.sort()).toEqual(['products', 'warehouses']);
    expect(session.accessToken()).toBe('access-second');
  });

  it('does not refresh or replay a request explicitly marked as non-idempotent', () => {
    authenticate();
    let receivedStatus: number | undefined;

    http
      .patch(
        `${API_ORIGIN}/api/v1/inventory/product-a/adjustments`,
        { quantityDelta: 1 },
        { context: new HttpContext().set(DISABLE_AUTH_REPLAY, true) },
      )
      .subscribe({
        error: (error: HttpErrorResponse) => {
          receivedStatus = error.status;
        },
      });

    httpTesting
      .expectOne(`${API_ORIGIN}/api/v1/inventory/product-a/adjustments`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(receivedStatus).toBe(401);
    httpTesting.expectNone(`${API_ORIGIN}/api/v1/auth/refresh`);
  });

  it('clears memory and releases every waiter when refresh returns 401', async () => {
    authenticate();
    const router = TestBed.inject(Router);
    const navigation = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const errors: unknown[] = [];

    http.get(`${API_ORIGIN}/api/v1/products`).subscribe({ error: (error) => errors.push(error) });
    http.get(`${API_ORIGIN}/api/v1/warehouses`).subscribe({ error: (error) => errors.push(error) });
    httpTesting
      .match((request) => request.url.endsWith('/products') || request.url.endsWith('/warehouses'))
      .forEach((request) => request.flush(null, { status: 401, statusText: 'Unauthorized' }));

    httpTesting
      .expectOne(`${API_ORIGIN}/api/v1/auth/refresh`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await Promise.resolve();

    expect(errors).toHaveLength(2);
    expect(errors.every((error) => error instanceof HttpErrorResponse)).toBe(true);
    expect(session.isAuthenticated()).toBe(false);
    expect(session.accessToken()).toBeNull();
    expect(navigation).toHaveBeenCalledOnce();
    expect(navigation).toHaveBeenCalledWith(['/login']);
  });

  it('does not replace either credential when a refresh response is incomplete', () => {
    authenticate();
    let receivedError: unknown;

    session.refreshAccessToken().subscribe({ error: (error) => (receivedError = error) });
    httpTesting.expectOne(`${API_ORIGIN}/api/v1/auth/refresh`).flush({
      ...SECOND_PAIR,
      refreshToken: undefined,
    });

    expect(receivedError).toBeInstanceOf(Error);
    expect(session.accessToken()).toBe('access-first');
  });

  it('clears memory immediately and completes logout even if revocation fails', () => {
    authenticate();
    let completed = false;

    session.logout().subscribe({ complete: () => (completed = true) });

    expect(session.isAuthenticated()).toBe(false);
    expect(session.accessToken()).toBeNull();
    const logoutRequest = httpTesting.expectOne(`${API_ORIGIN}/api/v1/auth/logout`);
    expect(logoutRequest.request.headers.has('Authorization')).toBe(false);
    expect(logoutRequest.request.body).toEqual({ refreshToken: 'refresh-first' });
    logoutRequest.flush(null, { status: 503, statusText: 'Unavailable' });
    expect(completed).toBe(true);
  });

  it('starts without a session after a simulated page reload', () => {
    authenticate();
    httpTesting.verify();
    TestBed.resetTestingModule();
    configureTestingModule();

    expect(session.isAuthenticated()).toBe(false);
    expect(session.accessToken()).toBeNull();
    expect(session.user()).toBeNull();
  });

  it.each([
    'https://outside.example.test/api/v1/products',
    'https://api.example.test.evil.invalid/api/v1/products',
  ])('never sends or refreshes credentials for destination %s', (destination) => {
    authenticate();
    let receivedError: unknown;

    http.get(destination).subscribe({ error: (error) => (receivedError = error) });
    const request = httpTesting.expectOne(destination);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).toBeInstanceOf(HttpErrorResponse);
    httpTesting.expectNone(`${API_ORIGIN}/api/v1/auth/refresh`);
  });

  it.each(['login', 'refresh', 'logout'])(
    'does not attach a token or start a refresh cycle for auth/%s',
    (endpoint) => {
      authenticate();
      let receivedError: unknown;
      const url = `${API_ORIGIN}/api/v1/auth/${endpoint}`;

      http.post(url, {}).subscribe({ error: (error) => (receivedError = error) });
      const request = httpTesting.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(receivedError).toBeInstanceOf(HttpErrorResponse);
      httpTesting.expectNone(`${API_ORIGIN}/api/v1/auth/refresh`);
    },
  );
});
