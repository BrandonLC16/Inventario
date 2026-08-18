import { TestBed } from '@angular/core/testing';

import { DesignSystemDemo } from './design-system-demo';

describe('DesignSystemDemo', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DesignSystemDemo] }).compileComponents();
  });

  it('renders every shared state in a labelled landmark', () => {
    const fixture = TestBed.createComponent(DesignSystemDemo);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toContain('Sistema visual compartido');
    expect(element.querySelector('app-loading-state [role="status"]')).not.toBeNull();
    expect(element.querySelector('app-empty-state section[aria-label]')).not.toBeNull();
    expect(element.querySelector('app-recoverable-error [role="alert"]')).not.toBeNull();
    expect(element.querySelectorAll('app-operation-feedback')).toHaveLength(5);
    expect(element.querySelectorAll('section[aria-labelledby]')).toHaveLength(3);
  });

  it('dismisses feedback and leaves a keyboard-operable restore action', () => {
    const fixture = TestBed.createComponent(DesignSystemDemo);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const closeButton = element.querySelector<HTMLButtonElement>(
      'app-operation-feedback button[aria-label="Cerrar mensaje"]',
    );

    closeButton?.focus();
    closeButton?.click();
    fixture.detectChanges();

    const restoreButton = Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.includes('Mostrar mensaje descartable'),
    );
    expect(restoreButton).toBeTruthy();
    expect(restoreButton?.type).toBe('button');
  });

  it('traps focus in confirmation and restores it after Escape', async () => {
    const fixture = TestBed.createComponent(DesignSystemDemo);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    document.body.appendChild(element);
    const trigger = Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.includes('Abrir confirmación'),
    );

    trigger?.focus();
    trigger?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 200));

    const dialog = document.querySelector<HTMLElement>('[role="dialog"]');
    const confirmButton = Array.from(
      dialog?.querySelectorAll<HTMLButtonElement>('button') ?? [],
    ).find((button) => button.textContent?.includes('Confirmar'));
    expect(dialog?.textContent).toContain('Confirmar acción');
    expect(dialog?.contains(document.activeElement)).toBe(true);
    expect(confirmButton).toBeTruthy();

    const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    Object.defineProperty(escape, 'keyCode', { value: 27 });
    dialog?.dispatchEvent(escape);
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 200));
    fixture.detectChanges();

    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);
    element.remove();
  });
});
