import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, filter, finalize, map, of, startWith, switchMap, tap } from 'rxjs';

import { ProductResponse } from '../../core/api/generated/model/product-response';
import { ProductsApiAdapter } from '../../core/api/products-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { INVENTORY_MANAGEMENT_ROLES } from '../../core/navigation/app-navigation';
import { SessionService } from '../../core/session/session.service';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../shared/confirmation-dialog/confirmation-dialog';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import { ProductDeleteProblem } from './product-delete-problem';
import { productListQuery, productQueryParams } from './product-query';

type ProductDetailResult =
  | { readonly product: ProductResponse; readonly problem?: never }
  | { readonly product?: never; readonly problem: ApiProblem };

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    ApiErrorMessage,
    LoadingState,
    OperationFeedback,
    ProductDeleteProblem,
  ],
  templateUrl: './product-detail.html',
  styleUrl: './products.scss',
})
export class ProductDetail {
  private readonly productsApi = inject(ProductsApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly reloadRequests = new Subject<void>();
  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  protected readonly loading = signal(true);
  protected readonly deleting = signal(false);
  protected readonly product = signal<ProductResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly deleteProblem = signal<ApiProblem | null>(null);
  protected readonly canManage = computed(() =>
    this.session.hasAnyRole(INVENTORY_MANAGEMENT_ROLES),
  );
  protected readonly listQueryParams = productQueryParams(
    productListQuery(this.route.snapshot.queryParamMap),
  );
  protected readonly feedback = signal<string | null>(this.initialFeedback());

  constructor() {
    this.reloadRequests
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.problem.set(null);
        }),
        switchMap(() =>
          this.productsApi.get(this.id).pipe(
            map((product): ProductDetailResult => ({ product })),
            catchError((error: unknown) =>
              of<ProductDetailResult>({ problem: this.apiErrors.from(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result.problem) {
          this.product.set(null);
          this.problem.set(result.problem);
          return;
        }
        this.product.set(result.product);
      });
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected requestDelete(): void {
    const current = this.product();
    if (!this.canManage() || !current?.id || this.deleting()) {
      return;
    }

    const data: ConfirmationDialogData = {
      title: 'Dar de baja lógica el producto',
      message: `${current.name ?? 'Producto sin nombre'} · SKU ${current.sku ?? 'sin SKU'} · ID ${current.id}. La baja lógica es terminal para el catálogo operativo, no libera el SKU y no equivale a suspender el producto con active=false. La API validará stock, reservas y documentos pendientes. El Kardex histórico se conserva.`,
      confirmLabel: 'Dar de baja',
      destructive: true,
    };

    this.deleting.set(true);
    this.dialog
      .open(ConfirmationDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        tap(() => {
          this.deleteProblem.set(null);
        }),
        switchMap(() => this.productsApi.delete(current.id!)),
        finalize(() => this.deleting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () =>
          void this.router.navigate(['/products'], {
            queryParams: {
              ...this.listQueryParams,
              result: 'deleted',
              deletedProductId: current.id,
            },
          }),
        error: (error: unknown) => this.deleteProblem.set(this.apiErrors.from(error)),
      });
  }

  private initialFeedback(): string | null {
    switch (this.route.snapshot.queryParamMap.get('result')) {
      case 'created':
        return 'El producto se creó correctamente.';
      case 'updated':
        return 'Los cambios del producto se guardaron correctamente.';
      default:
        return null;
    }
  }
}
