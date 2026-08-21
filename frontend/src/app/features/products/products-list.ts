import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Subject,
  catchError,
  combineLatest,
  filter,
  finalize,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { PageResponseProductResponse } from '../../core/api/generated/model/page-response-product-response';
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
import { EmptyState } from '../../shared/empty-state/empty-state';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import {
  ProductListQuery,
  productApiRequest,
  productListQuery,
  productQueryParams,
  sameProductQuery,
} from './product-query';

type ProductLoadResult =
  | { readonly response: PageResponseProductResponse; readonly problem?: never }
  | { readonly response?: never; readonly problem: ApiProblem };

@Component({
  selector: 'app-products-list',
  standalone: true,
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ApiErrorMessage,
    EmptyState,
    LoadingState,
    OperationFeedback,
  ],
  templateUrl: './products-list.html',
  styleUrl: './products.scss',
})
export class ProductsList {
  private readonly productsApi = inject(ProductsApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly reloadRequests = new Subject<void>();

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponseProductResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly deleteProblem = signal<ApiProblem | null>(null);
  protected readonly deletingIds = signal<ReadonlySet<string>>(new Set());
  protected readonly feedback = signal<string | null>(
    this.route.snapshot.queryParamMap.get('result') === 'deleted'
      ? 'El producto se dio de baja correctamente.'
      : null,
  );
  protected readonly query = signal(productListQuery(this.route.snapshot.queryParamMap));
  protected readonly products = computed(() => this.page()?.content ?? []);
  protected readonly canManage = computed(() =>
    this.session.hasAnyRole(INVENTORY_MANAGEMENT_ROLES),
  );
  protected readonly filterForm = this.formBuilder.nonNullable.group({
    sku: [this.query().sku ?? ''],
    name: [this.query().name ?? ''],
    active: [this.query().active === undefined ? '' : String(this.query().active)],
  });

  constructor() {
    const queryChanges = this.route.queryParamMap.pipe(
      map((params) => productListQuery(params)),
      filter((nextQuery) => !sameProductQuery(nextQuery, this.query()) || this.loading()),
      tap((nextQuery) => {
        this.query.set(nextQuery);
        this.filterForm.setValue(
          {
            sku: nextQuery.sku ?? '',
            name: nextQuery.name ?? '',
            active: nextQuery.active === undefined ? '' : String(nextQuery.active),
          },
          { emitEvent: false },
        );
      }),
    );

    combineLatest([queryChanges, this.reloadRequests.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.problem.set(null);
        }),
        switchMap(([query]) =>
          this.productsApi.list(productApiRequest(query)).pipe(
            map((response): ProductLoadResult => ({ response })),
            catchError((error: unknown) =>
              of<ProductLoadResult>({ problem: this.apiErrors.from(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result.problem) {
          this.page.set(null);
          this.problem.set(result.problem);
          return;
        }
        this.page.set(result.response);
      });
  }

  protected applyFilters(): void {
    const values = this.filterForm.getRawValue();
    const active = values.active === 'true' ? true : values.active === 'false' ? false : undefined;
    const query: ProductListQuery = {
      page: 0,
      size: this.query().size,
      ...(values.sku.trim() ? { sku: values.sku.trim() } : {}),
      ...(values.name.trim() ? { name: values.name.trim() } : {}),
      ...(active === undefined ? {} : { active }),
    };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: productQueryParams(query),
    });
  }

  protected clearFilters(): void {
    this.filterForm.reset({ sku: '', name: '', active: '' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: productQueryParams({ page: 0, size: this.query().size }),
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: productQueryParams({ ...this.query(), page }),
    });
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected detailQueryParams(): Record<string, unknown> {
    return productQueryParams(this.query());
  }

  protected requestDelete(product: ProductResponse): void {
    if (!this.canManage() || !product.id || this.deletingIds().has(product.id)) {
      return;
    }

    const data: ConfirmationDialogData = {
      title: 'Dar de baja el producto',
      message: `Se dará de baja ${product.name ?? product.sku ?? 'el producto seleccionado'}.`,
      confirmLabel: 'Dar de baja',
      destructive: true,
    };

    this.dialog
      .open(ConfirmationDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        tap(() => {
          this.deleteProblem.set(null);
          this.setDeleting(product.id!, true);
        }),
        switchMap(() => this.productsApi.delete(product.id!)),
        finalize(() => this.setDeleting(product.id!, false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.feedback.set('El producto se dio de baja correctamente.');
          this.reloadRequests.next();
        },
        error: (error: unknown) => this.deleteProblem.set(this.apiErrors.from(error)),
      });
  }

  private setDeleting(id: string, deleting: boolean): void {
    const next = new Set(this.deletingIds());
    if (deleting) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.deletingIds.set(next);
  }
}
