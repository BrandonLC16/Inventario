import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [MatButtonModule],
  templateUrl: './empty-state.html',
  styleUrl: './empty-state.scss',
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly description = input.required<string>();
  readonly actionLabel = input<string>();
  readonly action = output<void>();
}
