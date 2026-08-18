import { TestBed } from '@angular/core/testing';

import { EmptyState } from './empty-state/empty-state';
import { LoadingState } from './loading-state/loading-state';
import { OperationFeedback } from './operation-feedback/operation-feedback';
import { RecoverableError } from './recoverable-error/recoverable-error';

describe('shared visual states', () => {
  it('announces loading without exposing the decorative spinner', () => {
    const fixture = TestBed.createComponent(LoadingState);
    fixture.componentRef.setInput('label', 'Cargando productos');
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="status"]')?.textContent).toContain('Cargando productos');
    expect(element.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(element.querySelector('mat-spinner')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('renders an empty state and emits its optional action', () => {
    const fixture = TestBed.createComponent(EmptyState);
    const action = vi.fn();
    fixture.componentRef.setInput('title', 'Sin resultados');
    fixture.componentRef.setInput('description', 'Cambia los filtros.');
    fixture.componentRef.setInput('actionLabel', 'Limpiar filtros');
    fixture.componentInstance.action.subscribe(action);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const region = element.querySelector('section');
    const button = element.querySelector<HTMLButtonElement>('button');

    expect(region?.getAttribute('aria-label')).toBe('Sin resultados');
    button?.click();
    expect(action).toHaveBeenCalledOnce();
  });

  it('announces a recoverable error, exposes support reference, and emits retry', () => {
    const fixture = TestBed.createComponent(RecoverableError);
    const retry = vi.fn();
    fixture.componentRef.setInput('message', 'La solicitud falló.');
    fixture.componentRef.setInput('correlationId', 'corr-demo');
    fixture.componentInstance.retry.subscribe(retry);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('corr-demo');
    element.querySelector<HTMLButtonElement>('button')?.click();
    expect(retry).toHaveBeenCalledOnce();
  });

  it('uses semantic feedback roles and emits dismiss', () => {
    const fixture = TestBed.createComponent(OperationFeedback);
    const dismiss = vi.fn();
    fixture.componentRef.setInput('tone', 'error');
    fixture.componentRef.setInput('message', 'No se guardó.');
    fixture.componentRef.setInput('dismissible', true);
    fixture.componentInstance.dismiss.subscribe(dismiss);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('No se guardó.');
    expect(element.querySelector<HTMLButtonElement>('button')?.getAttribute('aria-label')).toBe(
      'Cerrar mensaje',
    );
    element.querySelector<HTMLButtonElement>('button')?.click();
    expect(dismiss).toHaveBeenCalledOnce();
  });
});
