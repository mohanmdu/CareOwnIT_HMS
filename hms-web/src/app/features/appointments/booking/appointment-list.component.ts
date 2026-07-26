import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { Department } from '../../masters-admin/departments/department.model';
import { DepartmentService } from '../../masters-admin/departments/department.service';
import { NotificationService } from '../../../shared/services/notification.service';
import { TablePagination } from '../../../shared/table/table-pagination';
import { TableSearchComponent } from '../../../shared/table/table-search.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent, StatusBadgeTone } from '../../../shared/ui/status-badge/status-badge.component';
import { OpDirectBillingListEntry } from '../../direct-billing/op-direct-billing.model';
import { OpDirectBillingReceiptDialogComponent } from '../../direct-billing/op-direct-billing-receipt-dialog.component';
import { OpDirectBillingService } from '../../direct-billing/op-direct-billing.service';
import { AppointmentBillingDialogComponent } from './appointment-billing-dialog.component';
import { Appointment, AppointmentStatus, PAYMENT_MODE_LABELS } from './appointment.model';
import { AppointmentService } from './appointment.service';
import { CancelAppointmentDialogComponent, CancelAppointmentDialogResult } from './cancel-appointment-dialog.component';

const STATUS_TONE: Record<string, StatusBadgeTone> = {
  BOOKED: 'warning',
  CONFIRMED: 'info',
  CANCELLED: 'danger',
  COMPLETED: 'success'
};

interface StatusTab {
  label: string;
  status: AppointmentStatus | null;
  directBilling?: boolean;
}

const STATUS_TABS: StatusTab[] = [
  { label: 'All', status: null },
  { label: 'Pending', status: 'BOOKED' },
  { label: 'Cancelled', status: 'CANCELLED' },
  { label: 'Completed', status: 'COMPLETED' },
  { label: 'Direct Billing', status: null, directBilling: true }
];

function toIsoDate(date: Date | null): string | undefined {
  if (!date) {
    return undefined;
  }
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Consolidates the legacy's separate Doctor Appointment history / Department
 * Appointment / Approval screens into one filterable list - status tabs
 * (Pending doubles as the approval queue), a date range, a department
 * filter, and the same search+pagination pattern used across Masters &
 * Admin (TableSearchComponent/TablePagination).
 */
@Component({
  selector: 'app-appointment-list',
  standalone: true,
  imports: [
    TitleCasePipe,
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatProgressBarModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatPaginatorModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    TableSearchComponent
  ],
  templateUrl: './appointment-list.component.html',
  styleUrl: './appointment-list.component.scss'
})
export class AppointmentListComponent {
  private readonly service = inject(AppointmentService);
  private readonly departmentService = inject(DepartmentService);
  private readonly directBillingService = inject(OpDirectBillingService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly displayedColumns = ['patient', 'consultant', 'department', 'date', 'time', 'status', 'actions'];
  readonly directBillingColumns = [
    'patient',
    'invoiceNumber',
    'amount',
    'paymentMode',
    'billedBy',
    'billedAt',
    'directBillingActions'
  ];
  readonly statusTone = STATUS_TONE;
  readonly statusTabs = STATUS_TABS;
  readonly paymentModeLabels = PAYMENT_MODE_LABELS;

  appointments = signal<Appointment[]>([]);
  departments = signal<Department[]>([]);
  loading = signal(false);

  directBillingEntries = signal<OpDirectBillingListEntry[]>([]);
  loadingDirectBilling = signal(false);

  tabIndex = signal(0);
  // Default to today only - staff can widen or clear the range via the date
  // filters to pull up past/future appointments.
  fromDate = signal<Date | null>(new Date());
  toDate = signal<Date | null>(new Date());
  departmentId = signal<number | null>(null);
  searchTerm = signal('');

  isDirectBillingTab = computed(() => this.statusTabs[this.tabIndex()].directBilling === true);

  filteredAppointments = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return this.appointments();
    }
    return this.appointments().filter(
      (a) =>
        (a.patientName ?? '').toLowerCase().includes(term) ||
        (a.consultantName ?? '').toLowerCase().includes(term) ||
        (a.departmentName ?? '').toLowerCase().includes(term)
    );
  });
  pagination = new TablePagination(this.filteredAppointments);

  filteredDirectBillingEntries = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return this.directBillingEntries();
    }
    return this.directBillingEntries().filter(
      (entry) =>
        (entry.patientName ?? '').toLowerCase().includes(term) ||
        String(entry.invoiceNumber).includes(term)
    );
  });
  directBillingPagination = new TablePagination(this.filteredDirectBillingEntries);

  constructor() {
    this.refresh();
    this.departmentService.list().subscribe({
      next: (departments) => this.departments.set(departments.filter((d) => d.active)),
      error: () => this.notification.error('Failed to load departments.')
    });
  }

  refresh(): void {
    this.loading.set(true);
    const tab = this.statusTabs[this.tabIndex()];
    this.service
      .list({
        status: tab.status ?? undefined,
        fromDate: toIsoDate(this.fromDate()),
        toDate: toIsoDate(this.toDate()),
        departmentId: this.departmentId() ?? undefined
      })
      .subscribe({
        next: (appointments) => {
          this.appointments.set(appointments);
          this.pagination.reset();
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.notification.error('Failed to load appointments.');
        }
      });
  }

  refreshDirectBilling(): void {
    this.loadingDirectBilling.set(true);
    this.directBillingService
      .list({
        fromDate: toIsoDate(this.fromDate()),
        toDate: toIsoDate(this.toDate())
      })
      .subscribe({
        next: (entries) => {
          this.directBillingEntries.set(entries);
          this.directBillingPagination.reset();
          this.loadingDirectBilling.set(false);
        },
        error: () => {
          this.loadingDirectBilling.set(false);
          this.notification.error('Failed to load direct billing entries.');
        }
      });
  }

  onTabChange(index: number): void {
    this.tabIndex.set(index);
    if (this.statusTabs[index].directBilling) {
      this.refreshDirectBilling();
    } else {
      this.refresh();
    }
  }

  onFiltersChange(): void {
    if (this.isDirectBillingTab()) {
      this.refreshDirectBilling();
    } else {
      this.refresh();
    }
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.pagination.reset();
    this.directBillingPagination.reset();
  }

  directBillingPaymentModeLabel(entry: OpDirectBillingListEntry): string {
    return this.paymentModeLabels[entry.paymentMode];
  }

  viewDirectBillingReceipt(entry: OpDirectBillingListEntry): void {
    this.directBillingService.get(entry.id).subscribe({
      next: (receipt) => {
        this.dialog.open(OpDirectBillingReceiptDialogComponent, {
          width: '640px',
          maxWidth: '95vw',
          data: { receipt }
        });
      },
      error: () => this.notification.error('Failed to load receipt.')
    });
  }

  approve(appointment: Appointment): void {
    if (appointment.id === null) {
      return;
    }
    this.dialog
      .open(AppointmentBillingDialogComponent, {
        width: '640px',
        maxWidth: '95vw',
        data: { appointment }
      })
      .afterClosed()
      .subscribe(() => this.refresh());
  }

  cancel(appointment: Appointment): void {
    if (appointment.id === null) {
      return;
    }
    this.dialog
      .open(CancelAppointmentDialogComponent, {
        width: '420px',
        data: { patientName: appointment.patientName ?? 'this patient' }
      })
      .afterClosed()
      .subscribe((result?: CancelAppointmentDialogResult) => {
        if (!result || appointment.id === null) {
          return;
        }
        this.service.cancel(appointment.id, result.reason, result.cancelledBy).subscribe({
          next: () => {
            this.notification.success('Appointment cancelled.');
            this.refresh();
          },
          error: () => this.notification.error('Failed to cancel appointment.')
        });
      });
  }
}
