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
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, finalize, map, of, startWith, switchMap, tap } from 'rxjs';

import { ProductRequest } from '../../core/api/generated/model/product-request';
import { ProductResponse } from '../../core/api/generated/model/product-response';
import { ProductsApiAdapter } from '../../core/api/products-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import { productListQuery, productQueryParams } from './product-query';

type FormMode = 'create' | 'edit';
type EditLoadResult =
  | { readonly product: ProductResponse; readonly problem?: never }
  | { readonly product?: never; readonly problem: ApiProblem };

const nonBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length === 0
    ? { required: true }
    : null;
const twoDecimals: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '');
  return /^\d+(?:\.\d{1,2})?$/.test(value) ? null : { decimalScale: true };
};

@Component({
  selector: 'app-product-form',
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
  templateUrl: './product-form.html',
  styleUrl: './products.scss',
})
export class ProductForm {
  private readonly productsApi = inject(ProductsApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly reloadRequests = new Subject<void>();
  private readonly mode =
    (this.route.snapshot.data['formMode'] as FormMode | undefined) ?? 'create';
  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  protected readonly editing = this.mode === 'edit';
  protected readonly title = this.editing ? 'Editar producto' : 'Nuevo producto';
  protected readonly loading = signal(this.editing);
  protected readonly loadFailed = signal(false);
  protected readonly submitting = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly localError = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly listQueryParams = productQueryParams(
    productListQuery(this.route.snapshot.queryParamMap),
  );
  protected readonly cancelTarget = computed(() =>
    this.editing ? ['/products', this.id] : ['/products'],
  );
  protected readonly form = this.formBuilder.nonNullable.group({
    sku: ['', [nonBlank, Validators.maxLength(64)]],
    name: ['', [nonBlank, Validators.maxLength(160)]],
    description: ['', [Validators.maxLength(1000)]],
    price: [
      0,
      [Validators.required, Validators.min(0), Validators.max(9_999_999_999.99), twoDecimals],
    ],
    active: [true, [Validators.required]],
    minimumStock: [0, [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)]],
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.apiErrors.clearValidationErrors(this.form);
      this.localError.set(null);
    });

    if (this.editing) {
      this.loadProduct();
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
      this.localError.set('Revisa los campos señalados antes de guardar el producto.');
      return;
    }

    const request = this.requestBody();
    const operation = this.editing
      ? this.productsApi.update(this.id, request)
      : this.productsApi.create(request);
    this.submitting.set(true);

    operation
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (product) => this.handleSuccess(product),
        error: (error: unknown) => {
          const problem = this.apiErrors.from(error);
          this.apiErrors.applyValidationErrors(this.form, problem);
          this.problem.set(problem);
        },
      });
  }

  private loadProduct(): void {
    this.reloadRequests
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.loadFailed.set(false);
          this.problem.set(null);
        }),
        switchMap(() =>
          this.productsApi.get(this.id).pipe(
            map((product): EditLoadResult => ({ product })),
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
        const product = result.product;
        this.form.patchValue({
          sku: product.sku ?? '',
          name: product.name ?? '',
          description: product.description ?? '',
          price: product.price ?? 0,
          active: product.active ?? true,
        });
      });
  }

  private requestBody(): ProductRequest {
    const value = this.form.getRawValue();
    const base: ProductRequest = {
      sku: value.sku.trim(),
      name: value.name.trim(),
      ...(value.description.trim() ? { description: value.description.trim() } : {}),
      price: Number(value.price),
      active: value.active,
    };

    return this.editing ? base : { ...base, minimumStock: Number(value.minimumStock) };
  }

  private handleSuccess(product: ProductResponse): void {
    const id = product.id ?? (this.editing ? this.id : undefined);
    if (!id) {
      this.success.set('El producto se guardó correctamente.');
      return;
    }

    void this.router.navigate(['/products', id], {
      queryParams: {
        ...this.listQueryParams,
        result: this.editing ? 'updated' : 'created',
      },
    });
  }
}
