import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { SessionService } from './session.service';

const NON_REFRESHABLE_AUTH_PATHS = new Set([
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
]);

export const DISABLE_AUTH_REPLAY = new HttpContextToken<boolean>(() => false);

export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const runtimeConfig = inject(RuntimeConfigService);
  const document = inject(DOCUMENT);

  if (!runtimeConfig.matchesApiOrigin(request.url)) {
    return next(request);
  }

  const session = inject(SessionService);
  const requestUrl = new URL(request.url, document.baseURI);
  if (NON_REFRESHABLE_AUTH_PATHS.has(requestUrl.pathname)) {
    return next(request);
  }

  const accessToken = session.accessToken();
  const authenticatedRequest = accessToken
    ? request.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
    : request;

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      if (
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401 ||
        request.context.get(DISABLE_AUTH_REPLAY) ||
        !session.canRefresh()
      ) {
        return throwError(() => error);
      }

      return session
        .refreshAccessToken()
        .pipe(
          switchMap((refreshedAccessToken) =>
            next(
              request.clone({ setHeaders: { Authorization: `Bearer ${refreshedAccessToken}` } }),
            ),
          ),
        );
    }),
  );
};
