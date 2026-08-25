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

import { SupplierRequest } from '../../core/api/generated/model/supplier-request';
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

type FormMode = 'create' | 'edit';
type EditLoadResult =
  | { readonly supplier: SupplierResponse; readonly problem?: never }
  | { readonly supplier?: never; readonly problem: ApiProblem };

const nonBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length === 0
    ? { required: true }
    : null;

@Component({
  selector: 'app-supplier-form',
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
  templateUrl: './supplier-form.html',
  styleUrl: './suppliers.scss',
  providers: [RetryAfterTracker],
})
export class SupplierForm {
  private readonly suppliersApi = inject(SuppliersApiAdapter);
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
  private loadedActive = false;

  protected readonly retryAfter = inject(RetryAfterTracker);
  protected readonly editing = this.mode === 'edit';
  protected readonly title = this.editing ? 'Editar proveedor' : 'Nuevo proveedor';
  protected readonly loading = signal(this.editing);
  protected readonly loadFailed = signal(false);
  protected readonly submitting = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly localError = signal<string | null>(null);
  protected readonly listQueryParams = supplierQueryParams(
    supplierListQuery(this.route.snapshot.queryParamMap),
  );
  protected readonly cancelTarget = computed(() =>
    this.editing ? ['/suppliers', this.id] : ['/suppliers'],
  );
  protected readonly uniquenessConflict = computed(() => this.problem()?.category === 'conflict');
  protected readonly form = this.formBuilder.nonNullable.group({
    code: ['', [nonBlank, Validators.maxLength(32)]],
    legalName: ['', [nonBlank, Validators.maxLength(160)]],
    commercialName: ['', [Validators.maxLength(160)]],
    fiscalIdentifier: ['', [Validators.maxLength(32)]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.maxLength(32)]],
    active: [true, [Validators.required]],
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.apiErrors.clearValidationErrors(this.form);
      this.localError.set(null);
    });

    if (this.editing) {
      this.loadSupplier();
    }
  }

  protected retryLoad(): void {
    this.reloadRequests.next();
  }

  protected submit(): void {
    if (this.submitting() || this.loading() || this.retryAfter.blocked()) {
      return;
    }

    this.problem.set(null);
    this.loadFailed.set(false);
    this.localError.set(null);
    this.apiErrors.clearValidationErrors(this.form);
    this.normalizeFormValues();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.localError.set('Revisa los campos señalados antes de guardar el proveedor.');
      return;
    }

    const request = this.requestBody();
    if (this.editing && this.loadedActive && !request.active) {
      this.confirmDeactivation(request);
      return;
    }
    this.persist(request);
  }

  private confirmDeactivation(request: SupplierRequest): void {
    const data: ConfirmationDialogData = {
      title: 'Guardar y desactivar proveedor',
      message:
        'Se eliminarán sus preferencias de abastecimiento y no podrá usarse en nuevas órdenes. El historial de compras se conserva.',
      confirmLabel: 'Guardar y desactivar',
      destructive: true,
    };
    this.submitting.set(true);
    this.dialog
      .open(ConfirmationDialog, { data })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        switchMap(() => this.save(request)),
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (supplier) => this.handleSuccess(supplier),
        error: (error: unknown) => this.handleError(error),
      });
  }

  private persist(request: SupplierRequest): void {
    this.submitting.set(true);
    this.save(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (supplier) => this.handleSuccess(supplier),
        error: (error: unknown) => this.handleError(error),
      });
  }

  private save(request: SupplierRequest): Observable<SupplierResponse> {
    return this.editing
      ? this.suppliersApi.update(this.id, request)
      : this.suppliersApi.create(request);
  }

  private loadSupplier(): void {
    this.reloadRequests
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.loadFailed.set(false);
          this.problem.set(null);
        }),
        switchMap(() =>
          this.suppliersApi.get(this.id).pipe(
            map((supplier): EditLoadResult => ({ supplier })),
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
        const supplier = result.supplier;
        this.loadedActive = supplier.active ?? true;
        this.form.setValue({
          code: supplier.code ?? '',
          legalName: supplier.legalName ?? '',
          commercialName: supplier.commercialName ?? '',
          fiscalIdentifier: supplier.fiscalIdentifier ?? '',
          email: supplier.email ?? '',
          phone: supplier.phone ?? '',
          active: supplier.active ?? true,
        });
      });
  }

  private requestBody(): SupplierRequest {
    const value = this.form.getRawValue();
    const commercialName = value.commercialName.trim();
    const fiscalIdentifier = value.fiscalIdentifier.trim().toUpperCase();
    const email = value.email.trim().toLowerCase();
    const phone = value.phone.trim();
    return {
      code: value.code.trim().toUpperCase(),
      legalName: value.legalName.trim(),
      ...(commercialName ? { commercialName } : {}),
      ...(fiscalIdentifier ? { fiscalIdentifier } : {}),
      ...(email ? { email } : {}),
      ...(phone ? { phone } : {}),
      active: value.active,
    };
  }

  private normalizeFormValues(): void {
    const value = this.form.getRawValue();
    this.form.patchValue(
      {
        code: value.code.trim().toUpperCase(),
        legalName: value.legalName.trim(),
        commercialName: value.commercialName.trim(),
        fiscalIdentifier: value.fiscalIdentifier.trim().toUpperCase(),
        email: value.email.trim().toLowerCase(),
        phone: value.phone.trim(),
      },
      { emitEvent: false },
    );
  }

  private handleError(error: unknown): void {
    const problem = this.apiErrors.from(error);
    this.apiErrors.applyValidationErrors(this.form, problem);
    this.retryAfter.block(problem.retryAfterSeconds);
    this.problem.set(problem);
  }

  private handleSuccess(supplier: SupplierResponse): void {
    const id = supplier.id ?? (this.editing ? this.id : undefined);
    if (!id) {
      this.localError.set(
        'El servidor guardó el proveedor, pero no devolvió un identificador para mostrar el detalle.',
      );
      return;
    }

    void this.router.navigate(['/suppliers', id], {
      queryParams: {
        ...this.listQueryParams,
        result: this.editing ? 'updated' : 'created',
      },
    });
  }
}
