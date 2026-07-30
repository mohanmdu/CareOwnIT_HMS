import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { EmptyStateComponent } from '../../shared/ui/empty-state/empty-state.component';
import { NotificationService } from '../../shared/services/notification.service';
import { DoctorQueueAuditLog } from './doctor-queue.model';
import { DoctorQueueService } from './doctor-queue.service';

export interface DoctorQueueHistoryDialogData {
  entryId: number;
  patientName: string;
}

/** Read-only - shows one queue entry's own audit trail. Opened without subscribing to afterClosed(), same as viewDirectBillingReceipt(). */
@Component({
  selector: 'app-doctor-queue-history-dialog',
  standalone: true,
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatProgressBarModule, EmptyStateComponent],
  templateUrl: './doctor-queue-history-dialog.component.html',
  styleUrl: './doctor-queue-history-dialog.component.scss'
})
export class DoctorQueueHistoryDialogComponent {
  readonly data = inject<DoctorQueueHistoryDialogData>(MAT_DIALOG_DATA);
  private readonly service = inject(DoctorQueueService);
  private readonly notification = inject(NotificationService);

  logs = signal<DoctorQueueAuditLog[]>([]);
  loading = signal(true);

  constructor() {
    this.service.getEntryAuditLogs(this.data.entryId).subscribe({
      next: (logs) => {
        this.logs.set(logs);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load this entry\'s history.');
      }
    });
  }
}
