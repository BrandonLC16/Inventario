import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { filter, finalize, switchMap, take, tap } from 'rxjs';

import { InventoryResponse } from '../../core/api/generated/model/inventory-response';
import { InventoryApiAdapter, InventoryBalanceRow } from '../../core/api/inventory-api.adapter';
import { ApiErrorService, ApiProblem } from '../../core/http/api-error.service';
import { RetryAfterTracker } from '../../core/http/retry-after-tracker';
import { ApiErrorMessage } from '../../shared/api-error-message/api-error-message';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../shared/confirmation-dialog/confirmation-dialog';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';

type AdjustmentDirection = 'in' | 'out';

@Component({
  selector: 'app-inventory-adjustment',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ApiErrorMessage,
    OperationFeedback,
  ],
  providers: [RetryAfterTracker],
  templateUrl: './inventory-adjustment.html',
  styleUrl: './inventory-adjustment.scss',
})
export class InventoryAdjustment {
  readonly row = input.required<InventoryBalanceRow>();
  readonly warehouseId = input.required<string>();
  readonly locationLabel = input.required<string>();
  readonly adjusted = output<InventoryResponse>();
  readonly cancelled = output<void>();
  readonly busyChange = output<boolean>();

  private readonly inventoryApi = inject(InventoryApiAdapter);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);
  private readonly title = viewChild<ElementRef<HTMLElement>>('adjustmentTitle');
  private dialogRef: MatDialogRef<ConfirmationDialog, boolean> | null = null;

  protected readonly retryAfter = inject(RetryAfterTracker);
  protected readonly confirming = signal(false);
  protected readonly submitting = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly localError = signal<string | null>(null);
  protected readonly uncertainOutcome = signal(false);
  protected readonly form = this.formBuilder.nonNullable.group({
    direction: ['in' as AdjustmentDirection, [Validators.required]],
    quantity: [
      1,
      [
        Validators.required,
        Validators.min(1),
        Validators.max(2_147_483_647),
        Validators.pattern(/^\d+$/),
      ],
    ],
    reference: ['', [Validators.maxLength(128)]],
  });
  protected readonly busy = computed(() => this.confirming() || this.submitting());

  constructor() {
    setTimeout(() => this.title()?.nativeElement.focus());
    this.destroyRef.onDestroy(() => {
      this.dialogRef?.close(false);
    });
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.apiErrors.clearValidationErrors(this.form);
      this.problem.set(null);
      this.localError.set(null);
      this.uncertainOutcome.set(false);
    });
  }

  protected delta(): number {
    const value = this.form.getRawValue();
    const quantity = Number(value.quantity);
    return value.direction === 'out' ? -quantity : quantity;
  }

  protected projectedPhysical(): number {
    return (this.row().balance.quantity ?? 0) + this.delta();
  }

  protected projectedAvailable(): number {
    return (this.row().balance.availableQuantity ?? 0) + this.delta();
  }

  protected requestConfirmation(): void {
    if (this.busy() || this.retryAfter.blocked()) {
      return;
    }
    this.problem.set(null);
    this.localError.set(null);
    this.uncertainOutcome.set(false);
    this.apiErrors.clearValidationErrors(this.form);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.localError.set('Revisa los campos señalados antes de confirmar el ajuste.');
      return;
    }

    const value = this.form.getRawValue();
    const delta = this.delta();
    if (!Number.isSafeInteger(delta) || delta === 0) {
      this.localError.set('La cantidad debe producir un ajuste entero distinto de cero.');
      return;
    }
    if (
      value.direction === 'out' &&
      Number(value.quantity) > (this.row().balance.availableQuantity ?? 0)
    ) {
      this.localError.set(
        'La salida no puede superar las unidades disponibles; parte del stock puede estar reservado.',
      );
      return;
    }

    const data: ConfirmationDialogData = {
      title: 'Confirmar ajuste manual',
      message: this.confirmationSummary(delta, value.reference.trim()),
      confirmLabel: 'Aplicar ajuste',
      destructive: delta < 0,
    };
    this.confirming.set(true);
    this.busyChange.emit(true);
    this.dialogRef = this.dialog.open(ConfirmationDialog, { data, closeOnNavigation: true });
    this.dialogRef
      .afterClosed()
      .pipe(
        take(1),
        tap(() => {
          this.confirming.set(false);
          this.dialogRef = null;
        }),
        filter((confirmed): confirmed is true => confirmed === true),
        tap(() => this.submitting.set(true)),
        switchMap(() =>
          this.inventoryApi.adjustStock(this.warehouseId(), this.row().balance.productId!, {
            quantityDelta: delta,
            ...(value.reference.trim() ? { reference: value.reference.trim() } : {}),
          }),
        ),
        finalize(() => {
          this.confirming.set(false);
          this.submitting.set(false);
          this.busyChange.emit(false);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (balance) => this.adjusted.emit(balance),
        error: (error: unknown) => {
          const problem = this.apiErrors.from(error);
          this.apiErrors.applyValidationErrors(this.form, problem);
          this.retryAfter.block(problem.retryAfterSeconds);
          this.uncertainOutcome.set(problem.status === 0);
          this.problem.set(problem);
        },
      });
  }

  protected cancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }

  private confirmationSummary(delta: number, reference: string): string {
    const row = this.row();
    const sign = delta > 0 ? '+' : '';
    const referenceSummary = reference ? ` Referencia: ${reference}.` : ' Sin referencia.';
    return (
      `${row.product.sku} · ${row.product.name} en ${this.locationLabel()}. ` +
      `Saldo actual: ${row.balance.quantity} físicas, ${row.balance.reservedQuantity} reservadas y ${row.balance.availableQuantity} disponibles. ` +
      `Ajuste: ${sign}${delta}. Resultado previsto: ${this.projectedPhysical()} físicas y ${this.projectedAvailable()} disponibles.` +
      referenceSummary
    );
  }
}
