import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-recoverable-error',
  standalone: true,
  imports: [MatButtonModule],
  templateUrl: './recoverable-error.html',
  styleUrl: './recoverable-error.scss',
})
export class RecoverableError {
  readonly title = input('No pudimos cargar la información');
  readonly message = input.required<string>();
  readonly retryLabel = input('Reintentar');
  readonly correlationId = input<string>();
  readonly retry = output<void>();
}
