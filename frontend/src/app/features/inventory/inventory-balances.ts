import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import {
  InventoryApiAdapter,
  InventoryBalancePage,
  InventoryBalanceRow,
  MAIN_WAREHOUSE_ID,
} from '../../core/api/inventory-api.adapter';
import { PageResponseWarehouseResponse } from '../../core/api/generated/model/page-response-warehouse-response';
import { WarehouseResponse } from '../../core/api/generated/model/warehouse-response';
import { InventoryResponse } from '../../core/api/generated/model/inventory-response';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { INVENTORY_MANAGEMENT_ROLES } from '../../core/navigation/app-navigation';
import { SessionService } from '../../core/session/session.service';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import { EmptyState } from '../../shared/empty-state/empty-state';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import { InventoryAdjustment } from './inventory-adjustment';
import {
  DEFAULT_WAREHOUSE_SELECTOR_PAGE_SIZE,
  InventoryListQuery,
  inventoryListQuery,
  inventoryQueryParams,
} from './inventory-query';

interface InventoryRequest {
  readonly warehouseId: string;
  readonly mainAlias: boolean;
  readonly page: number;
  readonly size: number;
}

type InventoryLoadResult =
  | {
      readonly page: InventoryBalancePage;
      readonly selectedWarehouse: WarehouseResponse | null;
      readonly problem?: never;
    }
  | {
      readonly page?: never;
      readonly selectedWarehouse?: never;
      readonly problem: ApiProblem;
    };

type WarehouseLoadResult =
  | { readonly response: PageResponseWarehouseResponse; readonly problem?: never }
  | { readonly response?: never; readonly problem: ApiProblem };

@Component({
  selector: 'app-inventory-balances',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    ApiErrorMessage,
    EmptyState,
    LoadingState,
    OperationFeedback,
    InventoryAdjustment,
  ],
  templateUrl: './inventory-balances.html',
  styleUrl: './inventory-balances.scss',
})
export class InventoryBalances {
  private readonly inventoryApi = inject(InventoryApiAdapter);
  private readonly warehousesApi = inject(WarehousesApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly balanceReloadRequests = new Subject<void>();
  private readonly warehouseReloadRequests = new Subject<void>();

  protected readonly query = signal<InventoryListQuery>(
    inventoryListQuery(this.route.snapshot.queryParamMap),
  );
  protected readonly warehouseId = signal(MAIN_WAREHOUSE_ID);
  protected readonly mainAlias = signal(true);
  protected readonly selectedWarehouse = signal<WarehouseResponse | null>(null);
  protected readonly balanceLoading = signal(true);
  protected readonly balancePage = signal<InventoryBalancePage | null>(null);
  protected readonly balanceProblem = signal<ApiProblem | null>(null);
  protected readonly rows = signal<readonly InventoryBalanceRow[]>([]);
  protected readonly adjustmentRow = signal<InventoryBalanceRow | null>(null);
  protected readonly adjustmentBusy = signal(false);
  protected readonly adjustmentFeedback = signal<string | null>(null);
  protected readonly warehouseLoading = signal(true);
  protected readonly warehousePage = signal<PageResponseWarehouseResponse | null>(null);
  protected readonly warehouseProblem = signal<ApiProblem | null>(null);
  protected readonly warehouseOptions = computed(() => {
    const selected = this.selectedWarehouse();
    const options = (this.warehousePage()?.content ?? []).filter(
      (warehouse) => warehouse.id && warehouse.id !== MAIN_WAREHOUSE_ID,
    );
    if (selected?.id && !options.some((warehouse) => warehouse.id === selected.id)) {
      return [selected, ...options];
    }
    return options;
  });
  protected readonly locationLabel = computed(() => {
    if (this.mainAlias()) {
      return 'MAIN';
    }
    const warehouse = this.selectedWarehouse();
    return warehouse?.code ?? warehouse?.name ?? 'almacén seleccionado';
  });
  protected readonly canManage = computed(() =>
    this.session.hasAnyRole(INVENTORY_MANAGEMENT_ROLES),
  );

  constructor() {
    const balanceRequests = combineLatest([
      this.route.data,
      this.route.paramMap,
      this.route.queryParamMap,
    ]).pipe(
      map(([data, params, queryParams]): InventoryRequest => {
        const query = inventoryListQuery(queryParams);
        const mainAlias = data['inventoryScope'] !== 'warehouse';
        const warehouseId = mainAlias ? MAIN_WAREHOUSE_ID : (params.get('id') ?? '');
        this.adjustmentRow.set(null);
        this.adjustmentBusy.set(false);
        this.adjustmentFeedback.set(null);
        this.query.set(query);
        this.mainAlias.set(mainAlias);
        this.warehouseId.set(warehouseId);
        return { warehouseId, mainAlias, page: query.page, size: query.size };
      }),
      distinctUntilChanged(
        (left, right) =>
          left.warehouseId === right.warehouseId &&
          left.mainAlias === right.mainAlias &&
          left.page === right.page &&
          left.size === right.size,
      ),
    );

    combineLatest([balanceRequests, this.balanceReloadRequests.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.balanceLoading.set(true);
          this.balanceProblem.set(null);
        }),
        switchMap(([request]) => {
          const page = request.mainAlias
            ? this.inventoryApi.listMain({ page: request.page, size: request.size })
            : this.inventoryApi.listWarehouse(request.warehouseId, {
                page: request.page,
                size: request.size,
              });
          const selectedWarehouse = request.mainAlias
            ? of<WarehouseResponse | null>(null)
            : this.warehousesApi.get(request.warehouseId);

          return forkJoin({ page, selectedWarehouse }).pipe(
            map((result): InventoryLoadResult => result),
            catchError((error: unknown) =>
              of<InventoryLoadResult>({ problem: this.apiErrors.from(error) }),
            ),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.balanceLoading.set(false);
        if (result.problem) {
          this.balancePage.set(null);
          this.rows.set([]);
          this.selectedWarehouse.set(null);
          this.balanceProblem.set(result.problem);
          return;
        }
        this.balancePage.set(result.page);
        this.rows.set(result.page.rows);
        this.selectedWarehouse.set(result.selectedWarehouse);
      });

    const warehousePageChanges = this.route.queryParamMap.pipe(
      map((params) => inventoryListQuery(params).warehousePage),
      distinctUntilChanged(),
    );

    combineLatest([warehousePageChanges, this.warehouseReloadRequests.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.warehouseLoading.set(true);
          this.warehouseProblem.set(null);
        }),
        switchMap(([page]) =>
          this.warehousesApi.list({ page, size: DEFAULT_WAREHOUSE_SELECTOR_PAGE_SIZE }).pipe(
            map((response): WarehouseLoadResult => ({ response })),
            catchError((error: unknown) =>
              of<WarehouseLoadResult>({ problem: this.apiErrors.from(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.warehouseLoading.set(false);
        if (result.problem) {
          this.warehousePage.set(null);
          this.warehouseProblem.set(result.problem);
          return;
        }
        this.warehousePage.set(result.response);
      });
  }

  protected selectWarehouse(warehouseId: string): void {
    if (this.adjustmentBusy()) {
      return;
    }
    const target =
      warehouseId === MAIN_WAREHOUSE_ID
        ? ['/inventory']
        : ['/warehouses', warehouseId, 'inventory'];
    void this.router.navigate(target, {
      queryParams: inventoryQueryParams({ ...this.query(), page: 0 }),
    });
  }

  protected goToBalancePage(page: number): void {
    if (this.adjustmentBusy()) {
      return;
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventoryQueryParams({ ...this.query(), page }),
    });
  }

  protected goToWarehousePage(warehousePage: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventoryQueryParams({ ...this.query(), warehousePage }),
    });
  }

  protected retryBalances(): void {
    this.balanceReloadRequests.next();
  }

  protected retryWarehouses(): void {
    this.warehouseReloadRequests.next();
  }

  protected openAdjustment(row: InventoryBalanceRow): void {
    if (!this.canManage() || this.adjustmentBusy()) {
      return;
    }
    this.adjustmentFeedback.set(null);
    this.adjustmentRow.set(row);
  }

  protected closeAdjustment(): void {
    if (!this.adjustmentBusy()) {
      this.adjustmentRow.set(null);
    }
  }

  protected reconcileAdjustment(balance: InventoryResponse): void {
    const selected = this.adjustmentRow();
    if (
      !selected ||
      balance.warehouseId !== this.warehouseId() ||
      balance.productId !== selected.balance.productId
    ) {
      return;
    }
    const replaceBalance = (row: InventoryBalanceRow): InventoryBalanceRow =>
      row.balance.productId === balance.productId ? { ...row, balance } : row;
    this.rows.update((rows) => rows.map(replaceBalance));
    this.balancePage.update((page) =>
      page
        ? {
            ...page,
            response: {
              ...page.response,
              content: (page.response.content ?? []).map((current) =>
                current.productId === balance.productId ? balance : current,
              ),
            },
            rows: page.rows.map(replaceBalance),
          }
        : page,
    );
    this.adjustmentBusy.set(false);
    this.adjustmentRow.set(null);
    this.adjustmentFeedback.set('El ajuste se aplicó con el saldo confirmado por Inventory API.');
  }
}
