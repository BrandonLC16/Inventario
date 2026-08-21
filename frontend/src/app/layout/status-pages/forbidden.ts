import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="status-page" data-error-source="routing">
      <p class="status-page__code">403</p>
      <h1 tabindex="-1">Acceso no disponible</h1>
      <p>
        Tu sesión no incluye permiso para esta área. Esta página corresponde a una decisión de
        navegación; la API siempre vuelve a validar la autorización.
      </p>
      <a routerLink="/dashboard">Volver al resumen</a>
    </section>
  `,
  styleUrl: './status-pages.scss',
})
export class Forbidden {}
