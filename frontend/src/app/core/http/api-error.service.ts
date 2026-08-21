import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AbstractControl, FormArray, FormGroup } from '@angular/forms';

export const API_ERROR_CODES = [
  'INVALID_REQUEST',
  'VALIDATION_FAILED',
  'RESOURCE_NOT_FOUND',
  'CONFLICT',
  'DATA_INTEGRITY_VIOLATION',
  'AUTHENTICATION_FAILED',
  'RATE_LIMIT_EXCEEDED',
  'AUTHENTICATION_REQUIRED',
  'ACCESS_DENIED',
  'METHOD_NOT_ALLOWED',
  'UNSUPPORTED_MEDIA_TYPE',
  'INTERNAL_ERROR',
] as const;

export type ApiErrorCode = (typeof API_ERROR_CODES)[number];
export type ApiErrorCategory =
  | 'authentication'
  | 'authorization'
  | 'not-found'
  | 'conflict'
  | 'rate-limit'
  | 'validation'
  | 'invalid-request'
  | 'unexpected';

export interface ApiProblem {
  readonly status: number;
  readonly code: ApiErrorCode | 'UNKNOWN';
  readonly category: ApiErrorCategory;
  readonly title: string;
  readonly message: string;
  readonly validationErrors: Readonly<Record<string, string>>;
  readonly correlationId?: string;
  readonly retryAfterSeconds?: number;
}

interface ErrorPresentation {
  readonly category: ApiErrorCategory;
  readonly title: string;
  readonly message: string;
}

const PRESENTATIONS: Readonly<Record<ApiErrorCode, ErrorPresentation>> = {
  INVALID_REQUEST: {
    category: 'invalid-request',
    title: 'La solicitud no es válida',
    message: 'Revisa los datos enviados antes de intentarlo nuevamente.',
  },
  VALIDATION_FAILED: {
    category: 'validation',
    title: 'Hay campos que requieren atención',
    message: 'Corrige los campos señalados antes de continuar.',
  },
  RESOURCE_NOT_FOUND: {
    category: 'not-found',
    title: 'No se encontró el recurso',
    message: 'El recurso solicitado ya no existe o no está disponible.',
  },
  CONFLICT: {
    category: 'conflict',
    title: 'La operación entra en conflicto',
    message:
      'Actualiza la información y revisa su estado antes de decidir si vuelves a intentarlo.',
  },
  DATA_INTEGRITY_VIOLATION: {
    category: 'conflict',
    title: 'La operación entra en conflicto',
    message: 'La operación no puede completarse con el estado actual de los datos.',
  },
  AUTHENTICATION_FAILED: {
    category: 'authentication',
    title: 'No fue posible iniciar sesión',
    message: 'Verifica tus credenciales e intenta nuevamente.',
  },
  RATE_LIMIT_EXCEEDED: {
    category: 'rate-limit',
    title: 'Espera antes de volver a intentarlo',
    message: 'La acción está temporalmente limitada para proteger el servicio.',
  },
  AUTHENTICATION_REQUIRED: {
    category: 'authentication',
    title: 'La sesión ya no está disponible',
    message: 'Inicia sesión nuevamente para continuar.',
  },
  ACCESS_DENIED: {
    category: 'authorization',
    title: 'No tienes permiso para esta operación',
    message: 'Tu sesión está activa, pero el servidor rechazó esta acción.',
  },
  METHOD_NOT_ALLOWED: {
    category: 'invalid-request',
    title: 'La operación no está disponible',
    message: 'La acción solicitada no es compatible con este recurso.',
  },
  UNSUPPORTED_MEDIA_TYPE: {
    category: 'invalid-request',
    title: 'El formato enviado no es compatible',
    message: 'Revisa el formato de los datos antes de continuar.',
  },
  INTERNAL_ERROR: {
    category: 'unexpected',
    title: 'Ocurrió un error inesperado',
    message: 'Intenta nuevamente más tarde o comparte la referencia con soporte.',
  },
};

const FALLBACK_PRESENTATION: ErrorPresentation = {
  category: 'unexpected',
  title: 'No fue posible completar la solicitud',
  message: 'Intenta nuevamente más tarde o comparte la referencia con soporte.',
};

const STATUS_CODES: Readonly<Partial<Record<number, ApiErrorCode>>> = {
  401: 'AUTHENTICATION_REQUIRED',
  403: 'ACCESS_DENIED',
  404: 'RESOURCE_NOT_FOUND',
  409: 'CONFLICT',
  429: 'RATE_LIMIT_EXCEEDED',
};

const CORRELATION_ID_PATTERN = /^[A-Za-z0-9._-]{1,128}$/;
const VALIDATION_KEY_PATTERN = /^[A-Za-z0-9_.[\]-]{1,160}$/;
const MAX_VALIDATION_MESSAGE_LENGTH = 300;
const MAX_RETRY_AFTER_SECONDS = 86_400;

@Injectable({ providedIn: 'root' })
export class ApiErrorService {
  from(error: unknown, now = Date.now()): ApiProblem {
    if (!(error instanceof HttpErrorResponse)) {
      return this.problem(0, 'UNKNOWN', FALLBACK_PRESENTATION, {}, undefined, undefined);
    }

    const payload = this.record(error.error);
    const payloadCode = payload ? this.apiErrorCode(payload['code']) : undefined;
    const code = payloadCode ?? STATUS_CODES[error.status] ?? 'UNKNOWN';
    const presentation = code === 'UNKNOWN' ? FALLBACK_PRESENTATION : PRESENTATIONS[code];
    const validationErrors = payload ? this.validationErrors(payload['validationErrors']) : {};
    const correlationId = this.correlationId(error, payload);
    const retryAfterSeconds =
      error.status === 429 ? this.retryAfter(error.headers.get('Retry-After'), now) : undefined;

    return this.problem(
      error.status,
      code,
      presentation,
      validationErrors,
      correlationId,
      retryAfterSeconds,
    );
  }

  applyValidationErrors(form: FormGroup, problem: ApiProblem): readonly string[] {
    const unmatchedFields: string[] = [];

    for (const [field, message] of Object.entries(problem.validationErrors)) {
      const control = form.get(field.replace(/\[(\d+)\]/g, '.$1'));
      if (!control) {
        unmatchedFields.push(field);
        continue;
      }
      control.setErrors({ ...control.errors, api: message });
      control.markAsTouched();
    }

    return unmatchedFields;
  }

  clearValidationErrors(control: AbstractControl): void {
    const errors = control.errors;
    if (errors?.['api']) {
      const remainingErrors = { ...errors };
      delete remainingErrors['api'];
      control.setErrors(Object.keys(remainingErrors).length > 0 ? remainingErrors : null);
    }

    if (control instanceof FormGroup) {
      Object.values(control.controls).forEach((child) => this.clearValidationErrors(child));
    } else if (control instanceof FormArray) {
      control.controls.forEach((child) => this.clearValidationErrors(child));
    }
  }

  private problem(
    status: number,
    code: ApiErrorCode | 'UNKNOWN',
    presentation: ErrorPresentation,
    validationErrors: Readonly<Record<string, string>>,
    correlationId: string | undefined,
    retryAfterSeconds: number | undefined,
  ): ApiProblem {
    return {
      status,
      code,
      category: presentation.category,
      title: presentation.title,
      message: presentation.message,
      validationErrors,
      ...(correlationId ? { correlationId } : {}),
      ...(retryAfterSeconds !== undefined ? { retryAfterSeconds } : {}),
    };
  }

  private record(value: unknown): Readonly<Record<string, unknown>> | undefined {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
      ? (value as Readonly<Record<string, unknown>>)
      : undefined;
  }

  private apiErrorCode(value: unknown): ApiErrorCode | undefined {
    return typeof value === 'string' && API_ERROR_CODES.some((code) => code === value)
      ? (value as ApiErrorCode)
      : undefined;
  }

  private validationErrors(value: unknown): Readonly<Record<string, string>> {
    const source = this.record(value);
    if (!source) {
      return {};
    }

    const result: Record<string, string> = {};
    for (const [field, rawMessage] of Object.entries(source)) {
      if (!VALIDATION_KEY_PATTERN.test(field) || typeof rawMessage !== 'string') {
        continue;
      }
      const message = rawMessage.trim();
      if (message.length === 0 || message.length > MAX_VALIDATION_MESSAGE_LENGTH) {
        continue;
      }
      result[field] = message;
    }
    return result;
  }

  private correlationId(
    error: HttpErrorResponse,
    payload: Readonly<Record<string, unknown>> | undefined,
  ): string | undefined {
    const headerValue = error.headers.get('X-Correlation-ID');
    if (headerValue && CORRELATION_ID_PATTERN.test(headerValue)) {
      return headerValue;
    }

    const bodyValue = payload?.['correlationId'];
    return typeof bodyValue === 'string' && CORRELATION_ID_PATTERN.test(bodyValue)
      ? bodyValue
      : undefined;
  }

  private retryAfter(value: string | null, now: number): number | undefined {
    if (!value) {
      return undefined;
    }

    const normalized = value.trim();
    if (/^\d+$/.test(normalized)) {
      const seconds = Number(normalized);
      return Number.isSafeInteger(seconds) && seconds <= MAX_RETRY_AFTER_SECONDS
        ? seconds
        : undefined;
    }

    const retryAt = Date.parse(normalized);
    if (!Number.isFinite(retryAt) || retryAt <= now) {
      return undefined;
    }
    const seconds = Math.ceil((retryAt - now) / 1000);
    return seconds <= MAX_RETRY_AFTER_SECONDS ? seconds : undefined;
  }
}
