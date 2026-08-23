import { Component, computed, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { ApiProblem } from '../../core/http/api-error.service';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';

@Component({
  selector: 'app-product-delete-problem',
  standalone: true,
  imports: [RouterLink, MatButtonModule, ApiErrorMessage],
  template: `
    <app-api-error-message [problem]="problem()" />

    @if (isConflict()) {
      <aside class="product-delete-review" aria-labelledby="product-delete-review-title">
        <h2 id="product-delete-review-title">El producto se conserva</h2>
        <p>
          La API no autorizó la baja. Puede existir stock físico, una reserva o un documento
          pendiente, pero esta respuesta no identifica cuál. Revisa la operación sin eliminar ni
          ocultar el producto.
        </p>
        <nav aria-label="Enlaces para revisar la baja del producto">
          <a mat-stroked-button routerLink="/inventory">Revisar inventario MAIN</a>
          <a mat-stroked-button routerLink="/warehouses">Revisar otros almacenes</a>
          <a
            mat-stroked-button
            routerLink="/inventory/kardex"
            [queryParams]="{ productId: productId() }"
          >
            Revisar Kardex por ID
          </a>
        </nav>
      </aside>
    }
  `,
  styleUrl: './product-delete-problem.scss',
})
export class ProductDeleteProblem {
  readonly problem = input.required<ApiProblem>();
  readonly productId = input.required<string>();

  protected readonly isConflict = computed(() => this.problem().status === 409);
}
