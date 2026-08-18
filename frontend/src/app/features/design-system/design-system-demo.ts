import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';

import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../shared/confirmation-dialog/confirmation-dialog';
import { EmptyState } from '../../shared/empty-state/empty-state';
import { LoadingState } from '../../shared/loading-state/loading-state';
import { OperationFeedback } from '../../shared/operation-feedback/operation-feedback';
import { RecoverableError } from '../../shared/recoverable-error/recoverable-error';

@Component({
  selector: 'app-design-system-demo',
  standalone: true,
  imports: [EmptyState, LoadingState, MatButtonModule, OperationFeedback, RecoverableError],
  templateUrl: './design-system-demo.html',
  styleUrl: './design-system-demo.scss',
})
export class DesignSystemDemo {
  private readonly dialog = inject(MatDialog);

  protected readonly feedbackVisible = signal(true);
  protected readonly confirmationMessage = signal<string | undefined>(undefined);

  protected showFeedback(): void {
    this.feedbackVisible.set(true);
  }

  protected openConfirmation(): void {
    const data: ConfirmationDialogData = {
      title: 'Confirmar acción',
      message:
        'Revisa la información antes de continuar. La aplicación esperará la respuesta del servidor.',
      confirmLabel: 'Confirmar',
      cancelLabel: 'Volver',
    };

    this.dialog
      .open<ConfirmationDialog, ConfirmationDialogData, boolean>(ConfirmationDialog, {
        data,
        maxWidth: 'calc(100vw - 2rem)',
        width: '32rem',
        restoreFocus: true,
      })
      .afterClosed()
      .subscribe((confirmed) => {
        this.confirmationMessage.set(
          confirmed === true ? 'La acción fue confirmada.' : 'La acción fue cancelada.',
        );
      });
  }
}
