import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Observable,
  Subject,
  catchError,
  filter,
  finalize,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { WarehouseRequest } from '../../core/api/generated/model/warehouse-request';
import { WarehouseResponse } from '../../core/api/generated/model/warehouse-response';
import { WarehousesApiAdapter } from '../../core/api/warehouses-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../shared/confirmation-dialog/confirmation-dialog';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import { warehouseListQuery, warehouseQueryParams } from './warehouse-query';

type FormMode = 'create' | 'edit';
type EditLoadResult =
  | { readonly warehouse: WarehouseResponse; readonly problem?: never }
  | { readonly warehouse?: never; readonly problem: ApiProblem };

const nonBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length === 0
    ? { required: true }
    : null;

@Component({
  selector: 'app-warehouse-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    ApiErrorMessage,
    LoadingState,
    OperationFeedback,
  ],
  templateUrl: './warehouse-form.html',
  styleUrl: './warehouses.scss',
})
export class WarehouseForm {
  private readonly warehousesApi = inject(WarehousesApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly reloadRequests = new Subject<void>();
  private readonly mode =
    (this.route.snapshot.data['formMode'] as FormMode | undefined) ?? 'create';
  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  private initialActive = true;

  protected readonly editing = this.mode === 'edit';
  protected readonly title = this.editing ? 'Editar almacén' : 'Nuevo almacén';
  protected readonly loading = signal(this.editing);
  protected readonly loadFailed = signal(false);
  protected readonly submitting = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly localError = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly listQueryParams = warehouseQueryParams(
    warehouseListQuery(this.route.snapshot.queryParamMap),
  );
  protected readonly cancelTarget = computed(() =>
    this.editing ? ['/warehouses', this.id] : ['/warehouses'],
  );
  protected readonly form = this.formBuilder.nonNullable.group({
    code: ['', [nonBlank, Validators.maxLength(32)]],
    name: ['', [nonBlank, Validators.maxLength(160)]],
    description: ['', [Validators.maxLength(1000)]],
    active: [true, [Validators.required]],
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.apiErrors.clearValidationErrors(this.form);
      this.localError.set(null);
    });

    if (this.editing) {
      this.loadWarehouse();
    }
  }

  protected retryLoad(): void {
    this.reloadRequests.next();
  }

  protected submit(): void {
    if (this.submitting() || this.loading()) {
      return;
    }

    this.problem.set(null);
    this.loadFailed.set(false);
    this.localError.set(null);
    this.success.set(null);
    this.apiErrors.clearValidationErrors(this.form);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.localError.set('Revisa los campos señalados antes de guardar el almacén.');
      return;
    }

    const request = this.requestBody();
    if (this.editing && this.initialActive && !request.active) {
      this.confirmDeactivation(request);
      return;
    }

    this.save(request);
  }

  private loadWarehouse(): void {
    this.reloadRequests
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.loadFailed.set(false);
          this.problem.set(null);
        }),
        switchMap(() =>
          this.warehousesApi.get(this.id).pipe(
            map((warehouse): EditLoadResult => ({ warehouse })),
            catchError((error: unknown) =>
              of<EditLoadResult>({ problem: this.apiErrors.from(error) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result.problem) {
          this.loadFailed.set(true);
          this.problem.set(result.problem);
          return;
        }
        const warehouse = result.warehouse;
        this.initialActive = warehouse.active ?? true;
        this.form.setValue({
          code: warehouse.code ?? '',
          name: warehouse.name ?? '',
          description: warehouse.description ?? '',
          active: this.initialActive,
        });
      });
  }

  private confirmDeactivation(request: WarehouseRequest): void {
    const data: ConfirmationDialogData = {
      title: 'Desactivar almacén',
      message: `Guardar estos cambios desactivará ${request.name}. No podrá recibir nuevas operaciones.`,
      confirmLabel: 'Guardar y desactivar',
      destructive: true,
    };

    this.submitting.set(true);
    this.dialog
      .open(ConfirmationDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        switchMap(() => this.saveOperation(request)),
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (warehouse) => this.handleSuccess(warehouse),
        error: (error: unknown) => this.handleError(error),
      });
  }

  private save(request: WarehouseRequest): void {
    this.submitting.set(true);
    this.saveOperation(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (warehouse) => this.handleSuccess(warehouse),
        error: (error: unknown) => this.handleError(error),
      });
  }

  private saveOperation(request: WarehouseRequest): Observable<WarehouseResponse> {
    return this.editing
      ? this.warehousesApi.update(this.id, request)
      : this.warehousesApi.create(request);
  }

  private requestBody(): WarehouseRequest {
    const value = this.form.getRawValue();
    return {
      code: value.code.trim(),
      name: value.name.trim(),
      ...(value.description.trim() ? { description: value.description.trim() } : {}),
      active: value.active,
    };
  }

  private handleError(error: unknown): void {
    const problem = this.apiErrors.from(error);
    this.apiErrors.applyValidationErrors(this.form, problem);
    this.problem.set(problem);
  }

  private handleSuccess(warehouse: WarehouseResponse): void {
    const id = warehouse.id ?? (this.editing ? this.id : undefined);
    if (!id) {
      this.success.set('El almacén se guardó correctamente.');
      return;
    }

    void this.router.navigate(['/warehouses', id], {
      queryParams: {
        ...this.listQueryParams,
        result: this.editing ? 'updated' : 'created',
      },
    });
  }
}
