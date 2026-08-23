import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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

import { LowStockResponse } from '../../core/api/generated/model/low-stock-response';
import { PageResponseLowStockResponse } from '../../core/api/generated/model/page-response-low-stock-response';
import { WarehouseResponse } from '../../core/api/generated/model/warehouse-response';
import { InventoryApiAdapter, MAIN_WAREHOUSE_ID } from '../../core/api/inventory-api.adapter';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import { EmptyState } from '../../shared/empty-state/empty-state';
import { LoadingState } from '../../shared/loading-state/loading-state';
import {
  InventoryAlertsQuery,
  inventoryAlertsApiRequest,
  inventoryAlertsQuery,
  inventoryAlertsQueryParams,
} from './inventory-operations-query';

interface AlertsRequest extends InventoryAlertsQuery {
  readonly warehouseId: string;
  readonly mainAlias: boolean;
}

type AlertsLoadResult =
  | {
      readonly page: PageResponseLowStockResponse;
      readonly warehouse: WarehouseResponse | null;
      readonly problem?: never;
    }
  | { readonly page?: never; readonly warehouse?: never; readonly problem: ApiProblem };

@Component({
  selector: 'app-inventory-alerts',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    ApiErrorMessage,
    EmptyState,
    LoadingState,
  ],
  templateUrl: './inventory-alerts.html',
  styleUrl: './inventory-operations.scss',
})
export class InventoryAlerts {
  private readonly inventoryApi = inject(InventoryApiAdapter);
  private readonly warehousesApi = inject(WarehousesApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly reloadRequests = new Subject<void>();
  private readonly title = viewChild<ElementRef<HTMLElement>>('pageTitle');

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponseLowStockResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly warehouseId = signal(MAIN_WAREHOUSE_ID);
  protected readonly mainAlias = signal(true);
  protected readonly selectedWarehouse = signal<WarehouseResponse | null>(null);
  protected readonly query = signal<InventoryAlertsQuery>(
    inventoryAlertsQuery(this.route.snapshot.queryParamMap),
  );
  protected readonly alerts = computed(() => this.page()?.content ?? []);
  protected readonly locationLabel = computed(() =>
    this.mainAlias()
      ? 'MAIN'
      : (this.selectedWarehouse()?.code ??
        this.selectedWarehouse()?.name ??
        'almacén seleccionado'),
  );
  protected readonly filterForm = this.formBuilder.nonNullable.group({
    search: [this.query().search ?? ''],
    outOfStockOnly: [this.query().outOfStockOnly],
  });

  constructor() {
    const requests = combineLatest([
      this.route.data,
      this.route.paramMap,
      this.route.queryParamMap,
    ]).pipe(
      map(([data, params, queryParams]): AlertsRequest => {
        const query = inventoryAlertsQuery(queryParams);
        const mainAlias = data['inventoryScope'] !== 'warehouse';
        const warehouseId = mainAlias ? MAIN_WAREHOUSE_ID : (params.get('id') ?? '');
        return { warehouseId, mainAlias, ...query };
      }),
      distinctUntilChanged((left, right) => JSON.stringify(left) === JSON.stringify(right)),
      tap((request) => {
        this.query.set(request);
        this.mainAlias.set(request.mainAlias);
        this.warehouseId.set(request.warehouseId);
        this.selectedWarehouse.set(null);
        this.page.set(null);
        this.filterForm.setValue(
          {
            search: request.search ?? '',
            outOfStockOnly: request.outOfStockOnly,
          },
          { emitEvent: false },
        );
      }),
    );

    combineLatest([requests, this.reloadRequests.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.problem.set(null);
        }),
        switchMap(([request]) =>
          forkJoin({
            page: this.inventoryApi.listLowStock(
              request.warehouseId,
              inventoryAlertsApiRequest(request),
            ),
            warehouse: request.mainAlias
              ? of<WarehouseResponse | null>(null)
              : this.warehousesApi.get(request.warehouseId),
          }).pipe(
            map((result): AlertsLoadResult => result),
            catchError((error: unknown) =>
              of<AlertsLoadResult>({ problem: this.apiErrors.from(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        setTimeout(() => this.title()?.nativeElement.focus());
        if (result.problem) {
          this.problem.set(result.problem);
          return;
        }
        this.page.set(result.page);
        this.selectedWarehouse.set(result.warehouse);
      });
  }

  protected applyFilters(): void {
    const values = this.filterForm.getRawValue();
    const query: InventoryAlertsQuery = {
      page: 0,
      size: this.query().size,
      ...(values.search.trim() ? { search: values.search.trim() } : {}),
      outOfStockOnly: values.outOfStockOnly,
    };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventoryAlertsQueryParams(query),
    });
  }

  protected clearFilters(): void {
    this.filterForm.reset({ search: '', outOfStockOnly: false });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventoryAlertsQueryParams({
        page: 0,
        size: this.query().size,
        outOfStockOnly: false,
      }),
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventoryAlertsQueryParams({ ...this.query(), page }),
    });
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected inventoryRoute(): readonly string[] {
    return this.mainAlias() ? ['/inventory'] : ['/warehouses', this.warehouseId(), 'inventory'];
  }

  protected kardexRoute(): readonly string[] {
    return this.mainAlias()
      ? ['/inventory/kardex']
      : ['/warehouses', this.warehouseId(), 'inventory', 'kardex'];
  }

  protected alertLabel(alert: LowStockResponse): string {
    return alert.alert === 'OUT_OF_STOCK' ? 'Agotado' : 'Stock bajo';
  }
}
