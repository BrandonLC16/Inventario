import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, filter, finalize, map, of, startWith, switchMap, tap } from 'rxjs';

import { SupplierResponse } from '../../core/api/generated/model/supplier-response';
import { SuppliersApiAdapter } from '../../core/api/suppliers-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { RetryAfterTracker } from '../../core/http/retry-after-tracker';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../shared/confirmation-dialog/confirmation-dialog';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import { supplierListQuery, supplierQueryParams } from './supplier-query';

type SupplierDetailResult =
  | { readonly supplier: SupplierResponse; readonly problem?: never }
  | { readonly supplier?: never; readonly problem: ApiProblem };

@Component({
  selector: 'app-supplier-detail',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    ApiErrorMessage,
    LoadingState,
    OperationFeedback,
  ],
  templateUrl: './supplier-detail.html',
  styleUrl: './suppliers.scss',
  providers: [RetryAfterTracker],
})
export class SupplierDetail {
  private readonly suppliersApi = inject(SuppliersApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly reloadRequests = new Subject<void>();
  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  protected readonly retryAfter = inject(RetryAfterTracker);
  protected readonly loading = signal(true);
  protected readonly deactivating = signal(false);
  protected readonly supplier = signal<SupplierResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly deactivateProblem = signal<ApiProblem | null>(null);
  protected readonly listQueryParams = supplierQueryParams(
    supplierListQuery(this.route.snapshot.queryParamMap),
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
          this.suppliersApi.get(this.id).pipe(
            map((supplier): SupplierDetailResult => ({ supplier })),
            catchError((error: unknown) =>
              of<SupplierDetailResult>({ problem: this.apiErrors.from(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result.problem) {
          this.supplier.set(null);
          this.problem.set(result.problem);
          return;
        }
        this.supplier.set(result.supplier);
      });
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected requestDeactivate(): void {
    const current = this.supplier();
    if (
      !current?.id ||
      current.active === false ||
      this.deactivating() ||
      this.retryAfter.blocked()
    ) {
      return;
    }

    const data: ConfirmationDialogData = {
      title: 'Desactivar proveedor',
      message: `${current.legalName ?? 'Proveedor sin razón social'} · Código ${current.code ?? 'sin código'}. Se eliminarán sus preferencias de abastecimiento y no podrá usarse en nuevas órdenes. El historial de compras se conserva.`,
      confirmLabel: 'Desactivar',
      destructive: true,
    };

    this.deactivating.set(true);
    this.dialog
      .open(ConfirmationDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        tap(() => this.deactivateProblem.set(null)),
        switchMap(() => this.suppliersApi.deactivate(current.id!)),
        finalize(() => this.deactivating.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () =>
          void this.router.navigate(['/suppliers'], {
            queryParams: { ...this.listQueryParams, result: 'deactivated' },
          }),
        error: (error: unknown) => {
          const problem = this.apiErrors.from(error);
          this.retryAfter.block(problem.retryAfterSeconds);
          this.deactivateProblem.set(problem);
        },
      });
  }

  private initialFeedback(): string | null {
    switch (this.route.snapshot.queryParamMap.get('result')) {
      case 'created':
        return 'El proveedor se creó correctamente con los valores normalizados por el servidor.';
      case 'updated':
        return 'Los cambios del proveedor se guardaron y reconciliaron correctamente.';
      default:
        return null;
    }
  }
}
