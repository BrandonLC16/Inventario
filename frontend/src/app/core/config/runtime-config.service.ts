import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

interface RuntimeConfigDocument {
  readonly apiBaseUrl: string;
}

const RUNTIME_CONFIG_URL = 'config/runtime-config.json';

@Injectable({ providedIn: 'root' })
export class RuntimeConfigService {
  private readonly document = inject(DOCUMENT);
  private apiOrigin: string | undefined;

  get apiBaseUrl(): string {
    if (!this.apiOrigin) {
      throw new Error('Runtime configuration has not been loaded.');
    }

    return this.apiOrigin;
  }

  matchesApiOrigin(requestUrl: string): boolean {
    if (!this.apiOrigin) {
      return false;
    }

    try {
      return new URL(requestUrl, this.document.baseURI).origin === this.apiOrigin;
    } catch {
      return false;
    }
  }

  async load(http: HttpClient): Promise<void> {
    const value: unknown = await firstValueFrom(
      http.get<unknown>(RUNTIME_CONFIG_URL, { headers: { Accept: 'application/json' } }),
    );
    this.apiOrigin = this.parseApiOrigin(value);
  }

  private parseApiOrigin(value: unknown): string {
    if (!this.isRuntimeConfig(value)) {
      throw new Error('Runtime configuration must define apiBaseUrl.');
    }

    const url = new URL(value.apiBaseUrl, this.document.baseURI);
    const isHttp = url.protocol === 'http:' || url.protocol === 'https:';
    const isOriginOnly =
      url.pathname === '/' && !url.search && !url.hash && !url.username && !url.password;

    if (!isHttp || !isOriginOnly) {
      throw new Error('apiBaseUrl must be an HTTP(S) origin without credentials, path or query.');
    }

    return url.origin;
  }

  private isRuntimeConfig(value: unknown): value is RuntimeConfigDocument {
    return (
      typeof value === 'object' &&
      value !== null &&
      'apiBaseUrl' in value &&
      typeof value.apiBaseUrl === 'string' &&
      value.apiBaseUrl.trim().length > 0
    );
  }
}
