import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface QueueReasonDialogData {
  title: string;
  message?: string;
  reasonLabel?: string;
  reasonRequired: boolean;
  confirmLabel: string;
  destructive?: boolean;
}

export interface QueueReasonDialogResult {
  reason: string | null;
}

/** One generic reason-capturing dialog, reused for Cancel (required reason)/No-Show/No-Show-for-appointment (optional reason) - mirrors CancelAppointmentDialogComponent's shape. */
@Component({
  selector: 'app-queue-reason-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './queue-reason-dialog.component.html',
  styleUrl: './queue-reason-dialog.component.scss'
})
export class QueueReasonDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<QueueReasonDialogComponent, QueueReasonDialogResult>);
  readonly data = inject<QueueReasonDialogData>(MAT_DIALOG_DATA);

  reason = '';

  get isValid(): boolean {
    return !this.data.reasonRequired || this.reason.trim().length > 0;
  }

  submit(): void {
    if (!this.isValid) {
      return;
    }
    this.dialogRef.close({ reason: this.reason.trim() || null });
  }
}
