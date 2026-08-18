import { Component, input } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading-state',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  templateUrl: './loading-state.html',
  styleUrl: './loading-state.scss',
})
export class LoadingState {
  readonly label = input('Cargando contenido');
  readonly compact = input(false);
}
