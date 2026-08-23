import { DatePipe } from '@angular/common';
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
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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

import { StockMovementPageResponse } from '../../core/api/generated/model/stock-movement-page-response';
import { StockMovementResponse } from '../../core/api/generated/model/stock-movement-response';
import { WarehouseResponse } from '../../core/api/generated/model/warehouse-response';
import { InventoryApiAdapter, MAIN_WAREHOUSE_ID } from '../../core/api/inventory-api.adapter';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import { EmptyState } from '../../shared/empty-state/empty-state';
import { LoadingState } from '../../shared/loading-state/loading-state';
import {
  InventoryKardexQuery,
  MOVEMENT_TYPES,
  MovementType,
  instantToLocalDateTime,
  inventoryKardexApiRequest,
  inventoryKardexQuery,
  inventoryKardexQueryParams,
  localDateTimeToInstant,
} from './inventory-operations-query';

interface KardexRequest extends InventoryKardexQuery {
  readonly warehouseId: string;
  readonly mainAlias: boolean;
}

type KardexLoadResult =
  | {
      readonly page: StockMovementPageResponse;
      readonly warehouse: WarehouseResponse | null;
      readonly problem?: never;
    }
  | { readonly page?: never; readonly warehouse?: never; readonly problem: ApiProblem };

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const MOVEMENT_LABELS: Readonly<Record<MovementType, string>> = {
  INITIAL_STOCK: 'Stock inicial',
  MANUAL_IN: 'Entrada manual',
  MANUAL_OUT: 'Salida manual',
  ORDER_RESERVED: 'Reserva de pedido',
  ORDER_RESERVATION_RELEASED: 'Liberación de reserva',
  ORDER_CONFIRMED: 'Pedido confirmado',
  ORDER_CANCELLED: 'Pedido cancelado',
  PURCHASE_RECEIVED: 'Compra recibida',
  TRANSFER_OUT: 'Salida por transferencia',
  TRANSFER_IN: 'Entrada por transferencia',
  PHYSICAL_COUNT_ADJUSTMENT: 'Ajuste por conteo',
};

@Component({
  selector: 'app-inventory-kardex',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ApiErrorMessage,
    EmptyState,
    LoadingState,
  ],
  templateUrl: './inventory-kardex.html',
  styleUrl: './inventory-operations.scss',
})
export class InventoryKardex {
  private readonly inventoryApi = inject(InventoryApiAdapter);
  private readonly warehousesApi = inject(WarehousesApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly reloadRequests = new Subject<void>();
  private readonly title = viewChild<ElementRef<HTMLElement>>('pageTitle');

  protected readonly movementTypes = MOVEMENT_TYPES;
  protected readonly loading = signal(true);
  protected readonly page = signal<StockMovementPageResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly localError = signal<string | null>(null);
  protected readonly warehouseId = signal(MAIN_WAREHOUSE_ID);
  protected readonly mainAlias = signal(true);
  protected readonly selectedWarehouse = signal<WarehouseResponse | null>(null);
  protected readonly query = signal<InventoryKardexQuery>(
    inventoryKardexQuery(this.route.snapshot.queryParamMap),
  );
  protected readonly movements = computed(() => this.page()?.content ?? []);
  protected readonly locationLabel = computed(() =>
    this.mainAlias()
      ? 'MAIN'
      : (this.selectedWarehouse()?.code ??
        this.selectedWarehouse()?.name ??
        'almacén seleccionado'),
  );
  protected readonly filterForm = this.formBuilder.nonNullable.group({
    productId: [this.query().productId ?? '', [Validators.pattern(UUID_PATTERN)]],
    type: [this.query().type ?? ''],
    from: [instantToLocalDateTime(this.query().from)],
    to: [instantToLocalDateTime(this.query().to)],
    reference: [this.query().reference ?? '', [Validators.maxLength(128)]],
  });

  constructor() {
    const requests = combineLatest([
      this.route.data,
      this.route.paramMap,
      this.route.queryParamMap,
    ]).pipe(
      map(([data, params, queryParams]): KardexRequest => {
        const query = inventoryKardexQuery(queryParams);
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
        this.localError.set(null);
        this.filterForm.setValue(
          {
            productId: request.productId ?? '',
            type: request.type ?? '',
            from: instantToLocalDateTime(request.from),
            to: instantToLocalDateTime(request.to),
            reference: request.reference ?? '',
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
            page: this.inventoryApi.listMovements(
              request.warehouseId,
              inventoryKardexApiRequest(request),
            ),
            warehouse: request.mainAlias
              ? of<WarehouseResponse | null>(null)
              : this.warehousesApi.get(request.warehouseId),
          }).pipe(
            map((result): KardexLoadResult => result),
            catchError((error: unknown) =>
              of<KardexLoadResult>({ problem: this.apiErrors.from(error) }),
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
    this.localError.set(null);
    if (this.filterForm.invalid) {
      this.filterForm.markAllAsTouched();
      this.localError.set('Revisa el producto y la referencia antes de aplicar los filtros.');
      return;
    }
    const values = this.filterForm.getRawValue();
    const from = localDateTimeToInstant(values.from);
    const to = localDateTimeToInstant(values.to);
    if ((values.from && !from) || (values.to && !to)) {
      this.localError.set('Introduce fechas y horas válidas.');
      return;
    }
    if (from && to && from > to) {
      this.localError.set('La fecha inicial no puede ser posterior a la fecha final.');
      return;
    }
    const type = MOVEMENT_TYPES.find((candidate) => candidate === values.type);
    const query: InventoryKardexQuery = {
      page: 0,
      size: this.query().size,
      ...(values.productId.trim() ? { productId: values.productId.trim() } : {}),
      ...(type ? { type } : {}),
      ...(from ? { from } : {}),
      ...(to ? { to } : {}),
      ...(values.reference.trim() ? { reference: values.reference.trim() } : {}),
    };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventoryKardexQueryParams(query),
    });
  }

  protected clearFilters(): void {
    this.filterForm.reset({ productId: '', type: '', from: '', to: '', reference: '' });
    this.localError.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventoryKardexQueryParams({ page: 0, size: this.query().size }),
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventoryKardexQueryParams({ ...this.query(), page }),
    });
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected inventoryRoute(): readonly string[] {
    return this.mainAlias() ? ['/inventory'] : ['/warehouses', this.warehouseId(), 'inventory'];
  }

  protected alertsRoute(): readonly string[] {
    return this.mainAlias()
      ? ['/inventory/alerts']
      : ['/warehouses', this.warehouseId(), 'inventory', 'alerts'];
  }

  protected movementLabel(movement: StockMovementResponse): string {
    return movement.movementType
      ? MOVEMENT_LABELS[movement.movementType]
      : 'Movimiento no identificado';
  }

  protected signed(value: number | undefined): string {
    const normalized = value ?? 0;
    return normalized > 0 ? `+${normalized}` : String(normalized);
  }
}
