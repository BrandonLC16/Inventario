import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

export type FeedbackTone = 'success' | 'info' | 'warning' | 'error';

const FEEDBACK_LABELS: Readonly<Record<FeedbackTone, string>> = {
  success: 'Operación completada',
  info: 'Información',
  warning: 'Atención',
  error: 'Operación no completada',
};

const FEEDBACK_MARKS: Readonly<Record<FeedbackTone, string>> = {
  success: '✓',
  info: 'i',
  warning: '!',
  error: '×',
};

@Component({
  selector: 'app-operation-feedback',
  standalone: true,
  imports: [MatButtonModule],
  templateUrl: './operation-feedback.html',
  styleUrl: './operation-feedback.scss',
})
export class OperationFeedback {
  readonly tone = input<FeedbackTone>('info');
  readonly message = input.required<string>();
  readonly dismissible = input(false);
  readonly dismiss = output<void>();

  protected readonly label = computed(() => FEEDBACK_LABELS[this.tone()]);
  protected readonly mark = computed(() => FEEDBACK_MARKS[this.tone()]);
}
