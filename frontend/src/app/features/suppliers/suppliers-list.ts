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

import { PageResponseSupplierResponse } from '../../core/api/generated/model/page-response-supplier-response';
import { SupplierResponse } from '../../core/api/generated/model/supplier-response';
import { SuppliersApiAdapter } from '../../core/api/suppliers-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { RetryAfterTracker } from '../../core/http/retry-after-tracker';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../shared/confirmation-dialog/confirmation-dialog';
import { EmptyState } from '../../shared/empty-state/empty-state';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import {
  SupplierListQuery,
  sameSupplierQuery,
  supplierApiRequest,
  supplierListQuery,
  supplierQueryParams,
} from './supplier-query';

type SupplierLoadResult =
  | { readonly response: PageResponseSupplierResponse; readonly problem?: never }
  | { readonly response?: never; readonly problem: ApiProblem };

@Component({
  selector: 'app-suppliers-list',
  standalone: true,
  imports: [
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
  templateUrl: './suppliers-list.html',
  styleUrl: './suppliers.scss',
  providers: [RetryAfterTracker],
})
export class SuppliersList {
  private readonly suppliersApi = inject(SuppliersApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly reloadRequests = new Subject<void>();

  protected readonly retryAfter = inject(RetryAfterTracker);
  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponseSupplierResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly deactivateProblem = signal<ApiProblem | null>(null);
  protected readonly deactivatingIds = signal<ReadonlySet<string>>(new Set());
  protected readonly feedback = signal<string | null>(
    this.route.snapshot.queryParamMap.get('result') === 'deactivated'
      ? 'El proveedor se desactivó correctamente. Ya no puede usarse en nuevas órdenes; su historial se conserva.'
      : null,
  );
  protected readonly query = signal(supplierListQuery(this.route.snapshot.queryParamMap));
  protected readonly suppliers = computed(() => this.page()?.content ?? []);
  protected readonly filterForm = this.formBuilder.nonNullable.group({
    code: [this.query().code ?? ''],
    name: [this.query().name ?? ''],
    fiscalIdentifier: [this.query().fiscalIdentifier ?? ''],
    active: [this.query().active === undefined ? '' : String(this.query().active)],
  });

  constructor() {
    const queryChanges = this.route.queryParamMap.pipe(
      map((params) => supplierListQuery(params)),
      filter((nextQuery) => !sameSupplierQuery(nextQuery, this.query()) || this.loading()),
      tap((nextQuery) => {
        this.query.set(nextQuery);
        this.filterForm.setValue(
          {
            code: nextQuery.code ?? '',
            name: nextQuery.name ?? '',
            fiscalIdentifier: nextQuery.fiscalIdentifier ?? '',
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
          this.suppliersApi.list(supplierApiRequest(query)).pipe(
            map((response): SupplierLoadResult => ({ response })),
            catchError((error: unknown) =>
              of<SupplierLoadResult>({ problem: this.apiErrors.from(error) }),
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
    const query: SupplierListQuery = {
      page: 0,
      size: this.query().size,
      ...(values.code.trim() ? { code: values.code.trim() } : {}),
      ...(values.name.trim() ? { name: values.name.trim() } : {}),
      ...(values.fiscalIdentifier.trim()
        ? { fiscalIdentifier: values.fiscalIdentifier.trim() }
        : {}),
      ...(active === undefined ? {} : { active }),
    };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: supplierQueryParams(query),
    });
  }

  protected clearFilters(): void {
    this.filterForm.reset({ code: '', name: '', fiscalIdentifier: '', active: '' });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: supplierQueryParams({ page: 0, size: this.query().size }),
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: supplierQueryParams({ ...this.query(), page }),
    });
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected detailQueryParams(): Record<string, unknown> {
    return supplierQueryParams(this.query());
  }

  protected requestDeactivate(supplier: SupplierResponse): void {
    if (
      !supplier.id ||
      supplier.active === false ||
      this.deactivatingIds().has(supplier.id) ||
      this.retryAfter.blocked()
    ) {
      return;
    }

    const data: ConfirmationDialogData = {
      title: 'Desactivar proveedor',
      message: `${supplier.legalName ?? 'Proveedor sin razón social'} · Código ${supplier.code ?? 'sin código'}. Se eliminarán sus preferencias de abastecimiento y no podrá usarse en nuevas órdenes. El historial de compras se conserva.`,
      confirmLabel: 'Desactivar',
      destructive: true,
    };

    this.setDeactivating(supplier.id, true);
    this.dialog
      .open(ConfirmationDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        tap(() => this.deactivateProblem.set(null)),
        switchMap(() => this.suppliersApi.deactivate(supplier.id!)),
        finalize(() => this.setDeactivating(supplier.id!, false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.feedback.set(
            'El proveedor se desactivó correctamente. Ya no puede usarse en nuevas órdenes; su historial se conserva.',
          );
          this.reloadRequests.next();
        },
        error: (error: unknown) => {
          const problem = this.apiErrors.from(error);
          this.retryAfter.block(problem.retryAfterSeconds);
          this.deactivateProblem.set(problem);
        },
      });
  }

  private setDeactivating(id: string, deactivating: boolean): void {
    const next = new Set(this.deactivatingIds());
    if (deactivating) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.deactivatingIds.set(next);
  }
}
