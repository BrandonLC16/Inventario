import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';

export interface ConfirmationDialogData {
  readonly title: string;
  readonly message: string;
  readonly confirmLabel: string;
  readonly cancelLabel?: string;
  readonly destructive?: boolean;
}

@Component({
  selector: 'app-confirmation-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle],
  templateUrl: './confirmation-dialog.html',
  styleUrl: './confirmation-dialog.scss',
})
export class ConfirmationDialog {
  protected readonly data = inject<ConfirmationDialogData>(MAT_DIALOG_DATA);
}
