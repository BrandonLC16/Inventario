import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, filter, finalize, map, of, startWith, switchMap, tap } from 'rxjs';

import { WarehouseResponse } from '../../core/api/generated/model/warehouse-response';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
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
import { warehouseListQuery, warehouseQueryParams } from './warehouse-query';

type WarehouseDetailResult =
  | { readonly warehouse: WarehouseResponse; readonly problem?: never }
  | { readonly warehouse?: never; readonly problem: ApiProblem };

@Component({
  selector: 'app-warehouse-detail',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    ApiErrorMessage,
    LoadingState,
    OperationFeedback,
  ],
  templateUrl: './warehouse-detail.html',
  styleUrl: './warehouses.scss',
})
export class WarehouseDetail {
  private readonly warehousesApi = inject(WarehousesApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly reloadRequests = new Subject<void>();
  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  protected readonly loading = signal(true);
  protected readonly deactivating = signal(false);
  protected readonly warehouse = signal<WarehouseResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly deactivateProblem = signal<ApiProblem | null>(null);
  protected readonly canManage = computed(() =>
    this.session.hasAnyRole(INVENTORY_MANAGEMENT_ROLES),
  );
  protected readonly listQueryParams = warehouseQueryParams(
    warehouseListQuery(this.route.snapshot.queryParamMap),
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
          this.warehousesApi.get(this.id).pipe(
            map((warehouse): WarehouseDetailResult => ({ warehouse })),
            catchError((error: unknown) =>
              of<WarehouseDetailResult>({ problem: this.apiErrors.from(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result.problem) {
          this.warehouse.set(null);
          this.problem.set(result.problem);
          return;
        }
        this.warehouse.set(result.warehouse);
      });
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected requestDeactivate(): void {
    const current = this.warehouse();
    if (!this.canManage() || !current?.id || current.active === false || this.deactivating()) {
      return;
    }

    const data: ConfirmationDialogData = {
      title: 'Desactivar almacén',
      message: `Se desactivará ${current.name ?? current.code ?? 'el almacén seleccionado'}. No podrá recibir nuevas operaciones.`,
      confirmLabel: 'Desactivar',
      destructive: true,
    };

    this.dialog
      .open(ConfirmationDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        tap(() => {
          this.deactivating.set(true);
          this.deactivateProblem.set(null);
        }),
        switchMap(() => this.warehousesApi.deactivate(current.id!)),
        finalize(() => this.deactivating.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () =>
          void this.router.navigate(['/warehouses'], {
            queryParams: { ...this.listQueryParams, result: 'deactivated' },
          }),
        error: (error: unknown) => this.deactivateProblem.set(this.apiErrors.from(error)),
      });
  }

  private initialFeedback(): string | null {
    switch (this.route.snapshot.queryParamMap.get('result')) {
      case 'created':
        return 'El almacén se creó correctamente.';
      case 'updated':
        return 'Los cambios del almacén se guardaron correctamente.';
      default:
        return null;
    }
  }
}
