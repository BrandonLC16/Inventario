import { ClipboardModule, Clipboard } from '@angular/cdk/clipboard';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { ApiProblem } from '../../core/http/api-error.service';
import { RetryAfterTracker } from '../../core/http/retry-after-tracker';

let nextCorrelationInputId = 0;

@Component({
  selector: 'app-api-error-message',
  standalone: true,
  imports: [ClipboardModule, MatButtonModule],
  providers: [RetryAfterTracker],
  templateUrl: './api-error-message.html',
  styleUrl: './api-error-message.scss',
})
export class ApiErrorMessage {
  private readonly clipboard = inject(Clipboard);
  protected readonly retryAfter = inject(RetryAfterTracker);
  protected readonly correlationInputId = `api-error-correlation-${nextCorrelationInputId++}`;

  readonly problem = input.required<ApiProblem>();
  readonly retryAllowed = input(false);
  readonly retryLabel = input('Reintentar');
  readonly retry = output<void>();

  protected readonly copyStatus = signal('');
  protected readonly validationEntries = computed(() =>
    Object.entries(this.problem().validationErrors),
  );

  private readonly synchronizeProblem = effect(() => {
    const problem = this.problem();
    this.retryAfter.block(problem.retryAfterSeconds);
    this.copyStatus.set('');
  });

  protected copyCorrelationId(): void {
    const correlationId = this.problem().correlationId;
    if (!correlationId) {
      return;
    }

    this.copyStatus.set(
      this.clipboard.copy(correlationId)
        ? 'Referencia copiada.'
        : 'No se pudo copiar. Selecciona la referencia manualmente.',
    );
  }
}
