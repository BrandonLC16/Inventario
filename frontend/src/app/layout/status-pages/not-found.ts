import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="status-page">
      <p class="status-page__code">404</p>
      <h1 tabindex="-1">Página no encontrada</h1>
      <p>La dirección no corresponde a una ruta disponible en el cliente de Inventario.</p>
      <a routerLink="/dashboard">Volver al resumen</a>
    </section>
  `,
  styleUrl: './status-pages.scss',
})
export class NotFound {}
