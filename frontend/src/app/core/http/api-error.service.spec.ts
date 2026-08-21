import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { FormArray, FormControl, FormGroup } from '@angular/forms';
import { TestBed } from '@angular/core/testing';

import { ApiErrorCode, ApiErrorService } from './api-error.service';

describe('ApiErrorService', () => {
  let service: ApiErrorService;

  beforeEach(() => {
    service = TestBed.inject(ApiErrorService);
  });

  function httpError(
    status: number,
    code?: ApiErrorCode,
    options: {
      readonly headers?: HttpHeaders;
      readonly body?: Readonly<Record<string, unknown>> | string;
    } = {},
  ): HttpErrorResponse {
    return new HttpErrorResponse({
      status,
      ...(options.headers ? { headers: options.headers } : {}),
      error:
        options.body ??
        ({
          code,
          message: 'variable internal detail with refreshToken=secret',
          error: 'database detail',
          path: '/internal/path',
          correlationId: 'corr-body',
          validationErrors: {},
        } satisfies Readonly<Record<string, unknown>>),
    });
  }

  it.each([
    [401, 'AUTHENTICATION_REQUIRED', 'authentication', 'La sesión ya no está disponible'],
    [403, 'ACCESS_DENIED', 'authorization', 'No tienes permiso para esta operación'],
    [404, 'RESOURCE_NOT_FOUND', 'not-found', 'No se encontró el recurso'],
    [409, 'CONFLICT', 'conflict', 'La operación entra en conflicto'],
    [429, 'RATE_LIMIT_EXCEEDED', 'rate-limit', 'Espera antes de volver a intentarlo'],
  ] satisfies readonly (readonly [number, ApiErrorCode, string, string])[])(
    'maps HTTP %i from its stable code',
    (status, code, category, title) => {
      const problem = service.from(httpError(status, code));

      expect(problem.category).toBe(category);
      expect(problem.title).toBe(title);
      expect(JSON.stringify(problem)).not.toContain('variable internal detail');
      expect(JSON.stringify(problem)).not.toContain('refreshToken=secret');
      expect(JSON.stringify(problem)).not.toContain('database detail');
      expect(JSON.stringify(problem)).not.toContain('/internal/path');
    },
  );

  it('uses the HTTP status as a safe fallback when code is missing', () => {
    const problem = service.from(httpError(404, undefined, { body: '<html>proxy error</html>' }));

    expect(problem.code).toBe('RESOURCE_NOT_FOUND');
    expect(problem.category).toBe('not-found');
    expect(JSON.stringify(problem)).not.toContain('proxy error');
  });

  it('returns a safe fallback for incomplete, non-HTTP, and unknown responses', () => {
    const incomplete = service.from(
      httpError(500, undefined, {
        body: { code: 'NOT_A_REAL_CODE', correlationId: '../unsafe', message: 'SQL detail' },
      }),
    );
    const nonHttp = service.from(new Error('access-first must stay private'));

    expect(incomplete.code).toBe('UNKNOWN');
    expect(incomplete.correlationId).toBeUndefined();
    expect(JSON.stringify(incomplete)).not.toContain('SQL detail');
    expect(nonHttp.status).toBe(0);
    expect(JSON.stringify(nonHttp)).not.toContain('access-first');
  });

  it('prefers a valid response header correlation ID and safely falls back to the body', () => {
    const fromHeader = service.from(
      httpError(409, 'CONFLICT', {
        headers: new HttpHeaders({ 'X-Correlation-ID': 'corr-header_01' }),
        body: { code: 'CONFLICT', correlationId: 'corr-body' },
      }),
    );
    const fromBody = service.from(
      httpError(409, 'CONFLICT', {
        headers: new HttpHeaders({ 'X-Correlation-ID': 'not safe/value' }),
        body: { code: 'CONFLICT', correlationId: 'corr-body' },
      }),
    );

    expect(fromHeader.correlationId).toBe('corr-header_01');
    expect(fromBody.correlationId).toBe('corr-body');
  });

  it('parses Retry-After seconds and HTTP dates without accepting unbounded values', () => {
    const now = Date.parse('2026-08-21T12:00:00Z');
    const seconds = service.from(
      httpError(429, 'RATE_LIMIT_EXCEEDED', {
        headers: new HttpHeaders({ 'Retry-After': '15' }),
      }),
      now,
    );
    const date = service.from(
      httpError(429, 'RATE_LIMIT_EXCEEDED', {
        headers: new HttpHeaders({ 'Retry-After': 'Fri, 21 Aug 2026 12:00:20 GMT' }),
      }),
      now,
    );
    const unsafe = service.from(
      httpError(429, 'RATE_LIMIT_EXCEEDED', {
        headers: new HttpHeaders({ 'Retry-After': '999999999' }),
      }),
      now,
    );

    expect(seconds.retryAfterSeconds).toBe(15);
    expect(date.retryAfterSeconds).toBe(20);
    expect(unsafe.retryAfterSeconds).toBeUndefined();
  });

  it('associates validated field errors with existing nested controls and clears only API errors', () => {
    const form = new FormGroup({
      sku: new FormControl('', { nonNullable: true }),
      items: new FormArray([
        new FormGroup({ quantity: new FormControl(0, { nonNullable: true }) }),
      ]),
    });
    form.controls.sku.setErrors({ required: true });
    const problem = service.from(
      httpError(400, 'VALIDATION_FAILED', {
        body: {
          code: 'VALIDATION_FAILED',
          validationErrors: {
            sku: 'El SKU ya está en uso.',
            'items[0].quantity': 'La cantidad está fuera de rango.',
            missing: 'Campo no presente.',
            '../unsafe': 'No debe aceptarse.',
            invalidValue: { message: 'No debe aceptarse.' },
          },
        },
      }),
    );

    expect(service.applyValidationErrors(form, problem)).toEqual(['missing']);
    expect(form.controls.sku.errors).toEqual({ required: true, api: 'El SKU ya está en uso.' });
    expect(form.controls.items.at(0).controls.quantity.getError('api')).toBe(
      'La cantidad está fuera de rango.',
    );
    expect(problem.validationErrors['../unsafe']).toBeUndefined();
    expect(problem.validationErrors['invalidValue']).toBeUndefined();

    service.clearValidationErrors(form);
    expect(form.controls.sku.errors).toEqual({ required: true });
    expect(form.controls.items.at(0).controls.quantity.errors).toBeNull();
  });
});
