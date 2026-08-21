import { Clipboard } from '@angular/cdk/clipboard';
import { TestBed } from '@angular/core/testing';

import { ApiProblem } from '../../core/http/api-error.service';
import { ApiErrorMessage } from './api-error-message';

const PROBLEM: ApiProblem = {
  status: 400,
  code: 'VALIDATION_FAILED',
  category: 'validation',
  title: 'Hay campos que requieren atención',
  message: 'Corrige los campos señalados antes de continuar.',
  correlationId: 'corr-visible-01',
  validationErrors: { sku: 'El SKU ya está en uso.' },
};

describe('ApiErrorMessage', () => {
  it('announces a safe summary, field errors, and a manually copyable support reference', () => {
    const fixture = TestBed.createComponent(ApiErrorMessage);
    fixture.componentRef.setInput('problem', PROBLEM);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const region = element.querySelector<HTMLElement>('[data-error-source="api"]');
    const input = element.querySelector<HTMLInputElement>('input[readonly]');
    const label = element.querySelector<HTMLLabelElement>('label');

    expect(region?.getAttribute('role')).toBe('alert');
    expect(region?.getAttribute('aria-live')).toBe('assertive');
    expect(region?.getAttribute('aria-atomic')).toBe('true');
    expect(region?.textContent).toContain('El SKU ya está en uso.');
    expect(element.querySelector('[aria-label="Resumen de campos con error"]')).not.toBeNull();
    expect(input?.value).toBe('corr-visible-01');
    expect(label?.htmlFor).toBe(input?.id);
    expect(element.querySelector('.api-error__retry')).toBeNull();
  });

  it('copies only the sanitized correlation ID and announces the result', () => {
    const clipboard = TestBed.inject(Clipboard);
    vi.spyOn(clipboard, 'copy').mockReturnValue(true);
    const fixture = TestBed.createComponent(ApiErrorMessage);
    fixture.componentRef.setInput('problem', PROBLEM);
    fixture.detectChanges();

    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'),
    ).find((candidate) => candidate.textContent?.includes('Copiar referencia'));
    button?.click();
    fixture.detectChanges();

    expect(clipboard.copy).toHaveBeenCalledWith('corr-visible-01');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Referencia copiada.');
  });

  it('requires explicit retry permission and disables it during Retry-After', () => {
    const retry = vi.fn();
    const fixture = TestBed.createComponent(ApiErrorMessage);
    fixture.componentRef.setInput('problem', { ...PROBLEM, retryAfterSeconds: 5 });
    fixture.componentRef.setInput('retryAllowed', true);
    fixture.componentInstance.retry.subscribe(retry);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const retryButton = element.querySelector<HTMLButtonElement>('.api-error__retry');
    expect(retryButton?.disabled).toBe(true);
    expect(element.querySelector('[role="status"]')?.textContent).toContain(
      'Podrás intentarlo nuevamente',
    );
    retryButton?.click();
    expect(retry).not.toHaveBeenCalled();
  });

  it('emits a retry only after an explicitly enabled manual action', () => {
    const retry = vi.fn();
    const fixture = TestBed.createComponent(ApiErrorMessage);
    fixture.componentRef.setInput('problem', PROBLEM);
    fixture.componentRef.setInput('retryAllowed', true);
    fixture.componentInstance.retry.subscribe(retry);
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.api-error__retry')
      ?.click();

    expect(retry).toHaveBeenCalledOnce();
  });
});
