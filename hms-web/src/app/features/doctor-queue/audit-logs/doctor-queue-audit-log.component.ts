import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { NotificationService } from '../../../shared/services/notification.service';
import { TablePagination } from '../../../shared/table/table-pagination';
import { TableSearchComponent } from '../../../shared/table/table-search.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent, StatusBadgeTone } from '../../../shared/ui/status-badge/status-badge.component';
import { DoctorQueueAuditLog } from '../doctor-queue.model';
import { DoctorQueueService } from '../doctor-queue.service';
import { DoctorQueueAuditChangesDialogComponent } from './doctor-queue-audit-changes-dialog.component';

// Assumption pending verification against the live backend - falls back to
// the raw operation string + a neutral tone if these don't match exactly.
const OPERATION_LABEL: Partial<Record<string, string>> = {
  CHECK_IN: 'Check-In',
  WALK_IN: 'Walk-In',
  ENTER_WAITING: 'Enter Waiting',
  CALL_NEXT: 'Next Patient',
  RECALL: 'Recall',
  SKIP: 'Skip',
  START_CONSULTATION: 'Start Consultation',
  COMPLETE: 'Complete',
  ESCALATE: 'Escalate',
  DE_ESCALATE: 'De-escalate',
  CANCEL: 'Cancel',
  CASCADE_CANCEL_FROM_APPOINTMENT: 'Cancelled (Appointment)',
  MOVE_TO_WAITING: 'Move to Waiting',
  NO_SHOW: 'No-Show',
  MARK_NO_SHOW_NO_CHECKIN: 'No-Show (Never Checked In)'
};

const OPERATION_TONE: Partial<Record<string, StatusBadgeTone>> = {
  CHECK_IN: 'info',
  WALK_IN: 'info',
  ENTER_WAITING: 'info',
  CALL_NEXT: 'success',
  RECALL: 'warning',
  SKIP: 'warning',
  START_CONSULTATION: 'success',
  COMPLETE: 'success',
  ESCALATE: 'danger',
  DE_ESCALATE: 'neutral',
  CANCEL: 'danger',
  CASCADE_CANCEL_FROM_APPOINTMENT: 'danger',
  MOVE_TO_WAITING: 'info',
  NO_SHOW: 'danger',
  MARK_NO_SHOW_NO_CHECKIN: 'danger'
};

/** Mirrors AppointmentAuditLogComponent's shape exactly - fetched once, no polling (back-office screen). */
@Component({
  selector: 'app-doctor-queue-audit-log',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatTableModule,
    MatProgressBarModule,
    MatPaginatorModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    TableSearchComponent
  ],
  templateUrl: './doctor-queue-audit-log.component.html',
  styleUrl: './doctor-queue-audit-log.component.scss'
})
export class DoctorQueueAuditLogComponent {
  private readonly service = inject(DoctorQueueService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly displayedColumns = [
    'serialNo',
    'operation',
    'patientName',
    'consultantName',
    'queueDate',
    'tokenDisplay',
    'performedAt',
    'performedBy',
    'actions'
  ];
  readonly operationLabel = OPERATION_LABEL;
  readonly operationTone = OPERATION_TONE;

  logs = signal<DoctorQueueAuditLog[]>([]);
  loading = signal(false);

  searchTerm = signal('');
  filteredLogs = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return this.logs();
    }
    return this.logs().filter(
      (log) =>
        (log.patientName ?? '').toLowerCase().includes(term) ||
        (log.consultantName ?? '').toLowerCase().includes(term) ||
        (log.performedBy ?? '').toLowerCase().includes(term) ||
        (this.operationLabel[log.operation] ?? log.operation).toLowerCase().includes(term)
    );
  });
  pagination = new TablePagination(this.filteredLogs);

  constructor() {
    this.loading.set(true);
    this.service.getAuditLogs().subscribe({
      next: (logs) => {
        this.logs.set(logs);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load audit logs.');
      }
    });
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.pagination.reset();
  }

  viewChanges(log: DoctorQueueAuditLog): void {
    this.dialog.open(DoctorQueueAuditChangesDialogComponent, {
      width: '480px',
      data: { log }
    });
  }
}
