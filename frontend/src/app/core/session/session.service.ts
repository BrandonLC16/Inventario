import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  Observable,
  catchError,
  defer,
  finalize,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';

import { AuthenticationService } from '../api/generated/api/authentication.service';
import { CurrentUserResponse } from '../api/generated/model/current-user-response';
import { LoginRequest } from '../api/generated/model/login-request';
import { TokenResponse } from '../api/generated/model/token-response';
import { AppRole, isAppRole } from '../navigation/app-navigation';

interface MemoryTokenPair {
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
  readonly refreshToken: string;
  readonly refreshTokenExpiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly authenticationApi = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly tokenPairState = signal<MemoryTokenPair | null>(null);
  private readonly userState = signal<CurrentUserResponse | null>(null);
  private refreshInFlight: Observable<string> | undefined;
  private refreshFlightId = 0;
  private generation = 0;

  readonly user = this.userState.asReadonly();
  readonly roles = computed<readonly AppRole[]>(() => this.readRoles(this.userState()));
  readonly isAuthenticated = computed(
    () => this.tokenPairState() !== null && this.userState() !== null,
  );

  login(request: LoginRequest): Observable<CurrentUserResponse> {
    this.clearMemory();
    const loginGeneration = this.generation;

    return this.authenticationApi.login({ loginRequest: request }).pipe(
      map((response) => this.requireTokenPair(response)),
      tap((pair) => this.replaceTokenPair(pair, loginGeneration)),
      switchMap(() => this.loadCurrentUser()),
      catchError((error: unknown) => {
        if (this.generation === loginGeneration) {
          this.clearMemory();
        }
        return throwError(() => error);
      }),
    );
  }

  loadCurrentUser(): Observable<CurrentUserResponse> {
    return this.authenticationApi.me().pipe(
      map((user) => ({ ...user, roles: new Set(this.readRoles(user)) })),
      tap((user) => this.userState.set(user)),
    );
  }

  logout(): Observable<void> {
    return defer(() => {
      const refreshToken = this.tokenPairState()?.refreshToken;
      this.clearMemory();

      if (!refreshToken) {
        return of(undefined);
      }

      return this.authenticationApi.logout({ refreshRequest: { refreshToken } }).pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      );
    });
  }

  accessToken(): string | null {
    return this.tokenPairState()?.accessToken ?? null;
  }

  canRefresh(): boolean {
    return this.tokenPairState() !== null;
  }

  hasAnyRole(allowedRoles: readonly AppRole[]): boolean {
    return this.roles().some((role) => allowedRoles.includes(role));
  }

  refreshAccessToken(): Observable<string> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }

    const currentPair = this.tokenPairState();
    if (!currentPair) {
      return throwError(() => new Error('No active session can be refreshed.'));
    }

    const refreshGeneration = this.generation;
    const flightId = ++this.refreshFlightId;
    const sharedRequest = defer(() =>
      this.authenticationApi.refresh({
        refreshRequest: { refreshToken: currentPair.refreshToken },
      }),
    ).pipe(
      map((response) => this.requireTokenPair(response)),
      tap((pair) => this.replaceTokenPair(pair, refreshGeneration)),
      map((pair) => pair.accessToken),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          if (this.generation === refreshGeneration) {
            this.clearMemory();
          }
          void this.router.navigate(['/login']);
        }
        return throwError(() => error);
      }),
      finalize(() => {
        if (this.refreshFlightId === flightId) {
          this.refreshInFlight = undefined;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    this.refreshInFlight = sharedRequest;
    return sharedRequest;
  }

  private clearMemory(): void {
    this.generation += 1;
    this.refreshFlightId += 1;
    this.refreshInFlight = undefined;
    this.tokenPairState.set(null);
    this.userState.set(null);
  }

  private replaceTokenPair(pair: MemoryTokenPair, expectedGeneration: number): void {
    if (this.generation !== expectedGeneration) {
      throw new Error('The session changed before credentials could be replaced.');
    }
    this.tokenPairState.set(pair);
  }

  private requireTokenPair(response: TokenResponse): MemoryTokenPair {
    if (
      response.tokenType !== 'Bearer' ||
      !response.accessToken ||
      !response.accessTokenExpiresAt ||
      !response.refreshToken ||
      !response.refreshTokenExpiresAt
    ) {
      throw new Error('The authentication response is incomplete.');
    }

    return {
      accessToken: response.accessToken,
      accessTokenExpiresAt: response.accessTokenExpiresAt,
      refreshToken: response.refreshToken,
      refreshTokenExpiresAt: response.refreshTokenExpiresAt,
    };
  }

  private readRoles(user: CurrentUserResponse | null): readonly AppRole[] {
    if (!user?.roles) {
      return [];
    }

    return Array.from(user.roles).filter((role): role is AppRole => isAppRole(role));
  }
}
