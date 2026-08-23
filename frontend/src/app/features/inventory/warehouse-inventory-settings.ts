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
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  EMPTY,
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  finalize,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { ProductResponse } from '../../core/api/generated/model/product-response';
import { InventorySettingResponse } from '../../core/api/generated/model/inventory-setting-response';
import { PageResponseInventorySettingResponse } from '../../core/api/generated/model/page-response-inventory-setting-response';
import { WarehouseResponse } from '../../core/api/generated/model/warehouse-response';
import { InventoryApiAdapter } from '../../core/api/inventory-api.adapter';
import { ProductsApiAdapter } from '../../core/api/products-api.adapter';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { RetryAfterTracker } from '../../core/http/retry-after-tracker';
import { INVENTORY_MANAGEMENT_ROLES } from '../../core/navigation/app-navigation';
import { SessionService } from '../../core/session/session.service';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import { EmptyState } from '../../shared/empty-state/empty-state';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import {
  InventorySettingsQuery,
  inventorySettingsQuery,
  inventorySettingsQueryParams,
} from './inventory-settings-query';

interface SettingsRequest {
  readonly warehouseId: string;
  readonly query: InventorySettingsQuery;
}

type SettingsLoadResult =
  | {
      readonly warehouse: WarehouseResponse;
      readonly page: PageResponseInventorySettingResponse;
      readonly problem?: never;
    }
  | { readonly warehouse?: never; readonly page?: never; readonly problem: ApiProblem };

interface DetailRequest {
  readonly warehouseId: string;
  readonly productId: string;
}

type DetailLoadResult =
  | {
      readonly request: DetailRequest;
      readonly setting: InventorySettingResponse;
      readonly product: ProductResponse;
      readonly problem?: never;
    }
  | {
      readonly request: DetailRequest;
      readonly setting?: never;
      readonly product?: never;
      readonly problem: ApiProblem;
    };

@Component({
  selector: 'app-warehouse-inventory-settings',
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
    OperationFeedback,
  ],
  providers: [RetryAfterTracker],
  templateUrl: './warehouse-inventory-settings.html',
  styleUrl: './warehouse-inventory-settings.scss',
})
export class WarehouseInventorySettings {
  private readonly inventoryApi = inject(InventoryApiAdapter);
  private readonly productsApi = inject(ProductsApiAdapter);
  private readonly warehousesApi = inject(WarehousesApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly reloadRequests = new Subject<void>();
  private readonly detailRequests = new Subject<DetailRequest | null>();
  private readonly editorTitle = viewChild<ElementRef<HTMLElement>>('editorTitle');

  protected readonly retryAfter = inject(RetryAfterTracker);
  protected readonly loading = signal(true);
  protected readonly warehouseId = signal('');
  protected readonly query = signal<InventorySettingsQuery>({ page: 0, size: 20 });
  protected readonly warehouse = signal<WarehouseResponse | null>(null);
  protected readonly page = signal<PageResponseInventorySettingResponse | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly selectedSetting = signal<InventorySettingResponse | null>(null);
  protected readonly globalProduct = signal<ProductResponse | null>(null);
  protected readonly detailLoading = signal(false);
  protected readonly detailProblem = signal<ApiProblem | null>(null);
  protected readonly submitting = signal(false);
  protected readonly saveProblem = signal<ApiProblem | null>(null);
  protected readonly localError = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly attemptedDeactivation = signal(false);
  protected readonly rows = computed(() => this.page()?.content ?? []);
  protected readonly canManage = computed(() =>
    this.session.hasAnyRole(INVENTORY_MANAGEMENT_ROLES),
  );
  protected readonly locationLabel = computed(
    () => this.warehouse()?.code ?? this.warehouse()?.name ?? 'almacén seleccionado',
  );
  protected readonly form = this.formBuilder.nonNullable.group({
    minimumStock: [
      0,
      [
        Validators.required,
        Validators.min(0),
        Validators.max(2_147_483_647),
        Validators.pattern(/^\d+$/),
      ],
    ],
    active: [true, [Validators.required]],
  });

  constructor() {
    const routeRequests = combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
      map(
        ([params, queryParams]): SettingsRequest => ({
          warehouseId: params.get('id') ?? '',
          query: inventorySettingsQuery(queryParams),
        }),
      ),
      distinctUntilChanged(
        (left, right) =>
          left.warehouseId === right.warehouseId &&
          left.query.page === right.query.page &&
          left.query.size === right.query.size,
      ),
      tap((request) => {
        this.warehouseId.set(request.warehouseId);
        this.query.set(request.query);
        this.closeSetting();
      }),
    );

    combineLatest([routeRequests, this.reloadRequests.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.problem.set(null);
        }),
        switchMap(([request]) =>
          forkJoin({
            warehouse: this.warehousesApi.get(request.warehouseId),
            page: this.inventoryApi.listSettings(request.warehouseId, request.query),
          }).pipe(
            map((result): SettingsLoadResult => result),
            catchError((error: unknown) =>
              of<SettingsLoadResult>({ problem: this.apiErrors.from(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result.problem) {
          this.warehouse.set(null);
          this.page.set(null);
          this.problem.set(result.problem);
          return;
        }
        this.warehouse.set(result.warehouse);
        this.page.set(result.page);
      });

    this.detailRequests
      .pipe(
        switchMap((request) => {
          if (!request) {
            return EMPTY;
          }
          this.detailLoading.set(true);
          this.detailProblem.set(null);
          return forkJoin({
            setting: this.inventoryApi.getSetting(request.warehouseId, request.productId),
            product: this.productsApi.get(request.productId),
          }).pipe(
            map(({ setting, product }): DetailLoadResult => {
              if (product.id !== request.productId) {
                throw new Error('Product detail does not match the requested setting.');
              }
              return { request, setting, product };
            }),
            catchError((error: unknown) =>
              of<DetailLoadResult>({ request, problem: this.apiErrors.from(error) }),
            ),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (
          result.request.warehouseId !== this.warehouseId() ||
          result.request.productId !== this.selectedSetting()?.productId
        ) {
          return;
        }
        this.detailLoading.set(false);
        if (result.problem) {
          this.detailProblem.set(result.problem);
          return;
        }
        this.selectedSetting.set(result.setting);
        this.globalProduct.set(result.product);
        this.form.reset({
          minimumStock: result.setting.minimumStock ?? 0,
          active: result.setting.active ?? false,
        });
      });

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.apiErrors.clearValidationErrors(this.form);
      this.localError.set(null);
      this.saveProblem.set(null);
      this.success.set(null);
    });
  }

  protected openSetting(setting: InventorySettingResponse): void {
    if (!setting.productId || !setting.warehouseId) {
      return;
    }
    this.selectedSetting.set(setting);
    this.globalProduct.set(null);
    this.detailProblem.set(null);
    this.saveProblem.set(null);
    this.localError.set(null);
    this.success.set(null);
    this.form.reset({
      minimumStock: setting.minimumStock ?? 0,
      active: setting.active ?? false,
    });
    this.detailRequests.next({ warehouseId: setting.warehouseId, productId: setting.productId });
    setTimeout(() => this.editorTitle()?.nativeElement.focus());
  }

  protected closeSetting(): void {
    this.detailRequests.next(null);
    this.selectedSetting.set(null);
    this.globalProduct.set(null);
    this.detailLoading.set(false);
    this.detailProblem.set(null);
    this.saveProblem.set(null);
    this.localError.set(null);
    this.success.set(null);
    this.retryAfter.block(undefined);
  }

  protected retry(): void {
    this.reloadRequests.next();
  }

  protected retryDetail(): void {
    const setting = this.selectedSetting();
    if (setting?.warehouseId && setting.productId) {
      this.detailRequests.next({ warehouseId: setting.warehouseId, productId: setting.productId });
    }
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: inventorySettingsQueryParams({ ...this.query(), page }),
    });
  }

  protected submit(): void {
    const setting = this.selectedSetting();
    if (
      !this.canManage() ||
      !setting?.productId ||
      !setting.warehouseId ||
      this.detailLoading() ||
      this.submitting() ||
      this.retryAfter.blocked()
    ) {
      return;
    }

    this.saveProblem.set(null);
    this.localError.set(null);
    this.success.set(null);
    this.apiErrors.clearValidationErrors(this.form);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.localError.set('Revisa el mínimo antes de guardar la configuración.');
      return;
    }

    const value = this.form.getRawValue();
    const warehouseId = setting.warehouseId;
    const productId = setting.productId;
    this.attemptedDeactivation.set(!value.active);
    this.submitting.set(true);
    this.inventoryApi
      .configureSetting(warehouseId, productId, {
        minimumStock: Number(value.minimumStock),
        active: value.active,
      })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (reconciled) => {
          if (
            warehouseId !== this.warehouseId() ||
            productId !== this.selectedSetting()?.productId
          ) {
            return;
          }
          this.selectedSetting.set(reconciled);
          this.page.update((current) =>
            current
              ? {
                  ...current,
                  content: (current.content ?? []).map((row) =>
                    row.productId === productId ? reconciled : row,
                  ),
                }
              : current,
          );
          this.form.reset({
            minimumStock: reconciled.minimumStock ?? 0,
            active: reconciled.active ?? false,
          });
          this.success.set('La configuración se guardó y se reconcilió con el servidor.');
        },
        error: (error: unknown) => {
          const problem = this.apiErrors.from(error);
          this.apiErrors.applyValidationErrors(this.form, problem);
          this.retryAfter.block(problem.retryAfterSeconds);
          this.saveProblem.set(problem);
        },
      });
  }
}
