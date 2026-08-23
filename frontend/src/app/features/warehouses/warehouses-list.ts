import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
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

import { PageResponseWarehouseResponse } from '../../core/api/generated/model/page-response-warehouse-response';
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
import { EmptyState } from '../../shared/empty-state/empty-state';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import {
  warehouseApiRequest,
  warehouseListQuery,
  warehouseQueryParams,
  sameWarehouseQuery,
} from './warehouse-query';

type WarehouseLoadResult =
  | { readonly response: PageResponseWarehouseResponse; readonly problem?: never }
  | { readonly response?: never; readonly problem: ApiProblem };

@Component({
  selector: 'app-warehouses-list',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    ApiErrorMessage,
    EmptyState,
    LoadingState,
    OperationFeedback,
  ],
  templateUrl: './warehouses-list.html',
  styleUrl: './warehouses.scss',
})
export class WarehousesList {
  private readonly warehousesApi = inject(WarehousesApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly reloadRequests = new Subject<void>();

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponseWarehouseResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly deactivateProblem = signal<ApiProblem | null>(null);
  protected readonly deactivatingIds = signal<ReadonlySet<string>>(new Set());
  protected readonly feedback = signal<string | null>(
    this.route.snapshot.queryParamMap.get('result') === 'deactivated'
      ? 'El almacén se desactivó correctamente.'
      : null,
  );
  protected readonly query = signal(warehouseListQuery(this.route.snapshot.queryParamMap));
  protected readonly warehouses = computed(() => this.page()?.content ?? []);
  protected readonly canManage = computed(() =>
    this.session.hasAnyRole(INVENTORY_MANAGEMENT_ROLES),
  );

  constructor() {
    const queryChanges = this.route.queryParamMap.pipe(
      map((params) => warehouseListQuery(params)),
      filter((nextQuery) => !sameWarehouseQuery(nextQuery, this.query()) || this.loading()),
      tap((nextQuery) => this.query.set(nextQuery)),
    );

    combineLatest([queryChanges, this.reloadRequests.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.problem.set(null);
        }),
        switchMap(([query]) =>
          this.warehousesApi.list(warehouseApiRequest(query)).pipe(
            map((response): WarehouseLoadResult => ({ response })),
            catchError((error: unknown) =>
              of<WarehouseLoadResult>({ problem: this.apiErrors.from(error) }),
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

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: warehouseQueryParams({ ...this.query(), page }),
    });
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected detailQueryParams(): Record<string, unknown> {
    return warehouseQueryParams(this.query());
  }

  protected requestDeactivate(warehouse: WarehouseResponse): void {
    if (
      !this.canManage() ||
      !warehouse.id ||
      warehouse.active === false ||
      this.deactivatingIds().has(warehouse.id)
    ) {
      return;
    }

    const data: ConfirmationDialogData = {
      title: 'Desactivar almacén',
      message: `Se desactivará ${warehouse.name ?? warehouse.code ?? 'el almacén seleccionado'}. No podrá recibir nuevas operaciones.`,
      confirmLabel: 'Desactivar',
      destructive: true,
    };

    this.dialog
      .open(ConfirmationDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        tap(() => {
          this.deactivateProblem.set(null);
          this.setDeactivating(warehouse.id!, true);
        }),
        switchMap(() => this.warehousesApi.deactivate(warehouse.id!)),
        finalize(() => this.setDeactivating(warehouse.id!, false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.feedback.set('El almacén se desactivó correctamente.');
          this.reloadRequests.next();
        },
        error: (error: unknown) => this.deactivateProblem.set(this.apiErrors.from(error)),
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
