import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EMPTY, Subject } from 'rxjs';
import { catchError, filter, switchMap } from 'rxjs/operators';
import { NotificationService } from '../../../shared/services/notification.service';
import { TablePagination } from '../../../shared/table/table-pagination';
import { TableSearchComponent } from '../../../shared/table/table-search.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent, StatusBadgeTone } from '../../../shared/ui/status-badge/status-badge.component';
import { Consultant } from '../../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { DoctorQueueHistoryDialogComponent } from '../doctor-queue-history-dialog.component';
import { DoctorQueueWorklistEntry, WorklistDisplayStatus } from '../doctor-queue.model';
import { DoctorQueueService } from '../doctor-queue.service';
import { QueueReasonDialogComponent, QueueReasonDialogResult } from '../queue-reason-dialog.component';
import { RegisterWalkInDialogComponent, RegisterWalkInDialogResult } from '../register-walk-in-dialog.component';

function toIsoDate(date: Date | null): string {
  if (!date) {
    return '';
  }
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

const STATUS_TONE: Record<string, StatusBadgeTone> = {
  BOOKED: 'warning',
  CHECKED_IN: 'info',
  WAITING: 'info',
  CALLED: 'success',
  IN_CONSULTATION: 'neutral',
  COMPLETED: 'success',
  SKIPPED: 'warning',
  NO_SHOW: 'danger',
  CANCELLED: 'danger'
};

interface StatusTab {
  label: string;
  statuses: WorklistDisplayStatus[] | null;
}

const STATUS_TABS: StatusTab[] = [
  { label: 'All', statuses: null },
  { label: 'Awaiting Check-In', statuses: ['BOOKED'] },
  { label: 'In Queue', statuses: ['CHECKED_IN', 'WAITING', 'CALLED', 'IN_CONSULTATION'] },
  { label: 'Completed', statuses: ['COMPLETED'] },
  { label: 'Skipped', statuses: ['SKIPPED'] },
  { label: 'No-Show / Cancelled', statuses: ['NO_SHOW', 'CANCELLED'] }
];

const POLL_MS = 15000;

/**
 * Folds the Check-In screen and the fuller audit/worklist screen into one -
 * both are driven by the exact same GET /worklist call, and splitting them
 * would force staff to tab-switch mid-workflow (check someone in, then need
 * to cancel a different row).
 */
@Component({
  selector: 'app-reception-worklist',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
    MatTooltipModule,
    EmptyStateComponent,
    PageHeaderComponent,
    StatusBadgeComponent,
    TableSearchComponent
  ],
  templateUrl: './reception-worklist.component.html',
  styleUrl: './reception-worklist.component.scss'
})
export class ReceptionWorklistComponent {
  private readonly service = inject(DoctorQueueService);
  private readonly consultantService = inject(ConsultantService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly statusTabs = STATUS_TABS;
  readonly statusTone = STATUS_TONE;
  readonly displayedColumns = ['token', 'patient', 'source', 'scheduledSlotTime', 'status', 'priority', 'actions'];

  consultants = signal<Consultant[]>([]);
  consultantId = signal<number | null>(null);
  queueDate = signal(new Date());
  tabIndex = signal(0);
  searchTerm = signal('');

  worklist = signal<DoctorQueueWorklistEntry[]>([]);
  loading = signal(false);

  private readonly refresh$ = new Subject<void>();

  readonly tabFilteredEntries = computed(() => {
    const tab = this.statusTabs[this.tabIndex()];
    const list = this.worklist();
    return tab.statuses === null ? list : list.filter((e) => tab.statuses!.includes(e.displayStatus));
  });

  readonly filteredEntries = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const list = this.tabFilteredEntries();
    if (!term) {
      return list;
    }
    return list.filter(
      (e) => e.patientName.toLowerCase().includes(term) || (e.tokenDisplay ?? '').toLowerCase().includes(term)
    );
  });

  pagination = new TablePagination(this.filteredEntries);

  constructor() {
    this.refresh$
      .pipe(
        filter(() => this.consultantId() !== null),
        switchMap(() => {
          this.loading.set(true);
          return this.service.getWorklist(this.consultantId()!, toIsoDate(this.queueDate())).pipe(
            catchError(() => {
              this.notification.error('Failed to load the queue worklist.');
              this.loading.set(false);
              return EMPTY;
            })
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe((entries) => {
        this.worklist.set(entries);
        this.pagination.reset();
        this.loading.set(false);
      });

    this.consultantService.list().subscribe({
      next: (consultants) => this.consultants.set(consultants),
      error: () => this.notification.error('Failed to load consultants.')
    });

    const handle = setInterval(() => this.refresh$.next(), POLL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(handle));
  }

  onFiltersChange(): void {
    this.pagination.reset();
    this.refresh$.next();
  }

  onTabChange(index: number): void {
    this.tabIndex.set(index);
    this.pagination.reset();
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.pagination.reset();
  }

  refresh(): void {
    this.refresh$.next();
  }

  checkIn(entry: DoctorQueueWorklistEntry): void {
    if (entry.appointmentId === null) {
      return;
    }
    this.service.checkIn(entry.appointmentId).subscribe({
      next: (result) => {
        this.notification.success(`Checked in - token ${result.tokenDisplay}.`);
        this.refresh();
      },
      error: (err) => this.notification.error(err.error?.message ?? 'Failed to check in.')
    });
  }

  noShowForAppointment(entry: DoctorQueueWorklistEntry): void {
    if (entry.appointmentId === null) {
      return;
    }
    this.dialog
      .open(QueueReasonDialogComponent, {
        width: '420px',
        data: {
          title: `Mark No-Show - ${entry.patientName}`,
          reasonRequired: false,
          confirmLabel: 'Mark No-Show',
          destructive: true
        }
      })
      .afterClosed()
      .subscribe((result?: QueueReasonDialogResult) => {
        if (result === undefined) {
          return;
        }
        this.service.noShowForAppointment(entry.appointmentId!, result.reason ?? undefined).subscribe({
          next: () => {
            this.notification.success('Marked as no-show.');
            this.refresh();
          },
          error: (err) => this.notification.error(err.error?.message ?? 'Failed to mark no-show.')
        });
      });
  }

  markNoShow(entry: DoctorQueueWorklistEntry): void {
    if (entry.queueEntryId === null) {
      return;
    }
    this.dialog
      .open(QueueReasonDialogComponent, {
        width: '420px',
        data: {
          title: `Mark No-Show - ${entry.patientName}`,
          reasonRequired: false,
          confirmLabel: 'Mark No-Show',
          destructive: true
        }
      })
      .afterClosed()
      .subscribe((result?: QueueReasonDialogResult) => {
        if (result === undefined) {
          return;
        }
        this.service.markNoShow(entry.queueEntryId!, result.reason ?? undefined).subscribe({
          next: () => {
            this.notification.success('Marked as no-show.');
            this.refresh();
          },
          error: (err) => this.notification.error(err.error?.message ?? 'Failed to mark no-show.')
        });
      });
  }

  cancelEntry(entry: DoctorQueueWorklistEntry): void {
    if (entry.queueEntryId === null) {
      return;
    }
    this.dialog
      .open(QueueReasonDialogComponent, {
        width: '420px',
        data: {
          title: `Cancel Queue Entry - ${entry.patientName}`,
          reasonRequired: true,
          confirmLabel: 'Cancel Entry',
          destructive: true
        }
      })
      .afterClosed()
      .subscribe((result?: QueueReasonDialogResult) => {
        if (result === undefined) {
          return;
        }
        this.service.cancel(entry.queueEntryId!, result.reason!).subscribe({
          next: () => {
            this.notification.success('Queue entry cancelled.');
            this.refresh();
          },
          error: (err) => this.notification.error(err.error?.message ?? 'Failed to cancel entry.')
        });
      });
  }

  moveToWaiting(entry: DoctorQueueWorklistEntry): void {
    if (entry.queueEntryId === null) {
      return;
    }
    this.service.moveToWaiting(entry.queueEntryId).subscribe({
      next: () => {
        this.notification.success('Moved back to waiting.');
        this.refresh();
      },
      error: (err) => this.notification.error(err.error?.message ?? 'Failed to move back to waiting.')
    });
  }

  viewHistory(entry: DoctorQueueWorklistEntry): void {
    if (entry.queueEntryId === null) {
      return;
    }
    this.dialog.open(DoctorQueueHistoryDialogComponent, {
      width: '520px',
      data: { entryId: entry.queueEntryId, patientName: entry.patientName }
    });
  }

  openRegisterWalkIn(): void {
    this.dialog
      .open(RegisterWalkInDialogComponent, {
        width: '480px',
        data: { consultants: this.consultants(), defaultConsultantId: this.consultantId() }
      })
      .afterClosed()
      .subscribe((result?: RegisterWalkInDialogResult) => {
        if (!result) {
          return;
        }
        this.service.registerWalkIn(result).subscribe({
          next: (entry) => {
            this.notification.success(`Walk-in registered - token ${entry.tokenDisplay}.`);
            this.refresh();
          },
          error: (err) => this.notification.error(err.error?.message ?? 'Failed to register walk-in.')
        });
      });
  }
}
