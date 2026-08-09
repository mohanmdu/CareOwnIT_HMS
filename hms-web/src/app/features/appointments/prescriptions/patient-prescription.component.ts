import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
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
import { NotificationService } from '../../../shared/services/notification.service';
import { TablePagination } from '../../../shared/table/table-pagination';
import { TableSearchComponent } from '../../../shared/table/table-search.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent, StatusBadgeTone } from '../../../shared/ui/status-badge/status-badge.component';
import { Consultant } from '../../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { OpCaseSheetDialogComponent } from './op-case-sheet-dialog.component';
import { OpCaseSheetViewDialogComponent } from './op-case-sheet-view-dialog.component';
import { PrescriptionWorklistEntry, PrescriptionWorklistSource } from './op-case-sheet.model';
import { OpCaseSheetService } from './op-case-sheet.service';

const CATEGORY_LABELS: Record<PrescriptionWorklistSource, string> = {
  APPOINTMENT: 'Appointment',
  DIRECT_BILLING: 'Direct Billing'
};

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
 * Patient Prescription worklist: document OP case sheets for appointments
 * (the Review Date Report that used to be a second tab here now lives at
 * its own Patient Registration route - see ReviewDateReportComponent).
 */
@Component({
  selector: 'app-patient-prescription',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusBadgeComponent,
    TableSearchComponent
  ],
  templateUrl: './patient-prescription.component.html',
  styleUrl: './patient-prescription.component.scss'
})
export class PatientPrescriptionComponent {
  private readonly service = inject(OpCaseSheetService);
  private readonly consultantService = inject(ConsultantService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly worklistColumns = ['patient', 'mobile', 'category', 'department', 'doctor', 'appointment', 'status', 'actions'];

  categoryLabel(source: PrescriptionWorklistSource): string {
    return CATEGORY_LABELS[source];
  }

  /** Case-sheet documentation status - separate from Category (what kind of visit) and Actions (what you can do about it). Same rule for both sources now that a walk-in can carry a case sheet too. */
  statusLabel(entry: PrescriptionWorklistEntry): string {
    return entry.hasCaseSheet ? 'Documented' : 'Pending';
  }

  statusTone(entry: PrescriptionWorklistEntry): StatusBadgeTone {
    return entry.hasCaseSheet ? 'success' : 'warning';
  }

  consultants = signal<Consultant[]>([]);
  worklist = signal<PrescriptionWorklistEntry[]>([]);
  loadingWorklist = signal(false);
  searchTerm = signal('');

  fromDate = signal<Date | null>(new Date());
  toDate = signal<Date | null>(new Date());
  consultantId = signal<number | null>(null);

  pagination = new TablePagination(this.worklist);

  constructor() {
    this.consultantService.list().subscribe({
      next: (consultants) => this.consultants.set(consultants),
      error: () => this.notification.error('Failed to load consultants.')
    });
    this.refreshWorklist();
  }

  refreshWorklist(): void {
    this.loadingWorklist.set(true);
    this.service
      .worklist({
        fromDate: toIsoDate(this.fromDate()),
        toDate: toIsoDate(this.toDate()),
        consultantId: this.consultantId() ?? undefined,
        search: this.searchTerm() || undefined
      })
      .subscribe({
        next: (entries) => {
          this.worklist.set(entries);
          this.pagination.reset();
          this.loadingWorklist.set(false);
        },
        error: () => {
          this.loadingWorklist.set(false);
          this.notification.error('Failed to load the prescription worklist.');
        }
      });
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.refreshWorklist();
  }

  openCaseSheet(entry: PrescriptionWorklistEntry): void {
    if (entry.appointmentId === null && entry.directBillingId === null) {
      return;
    }
    this.dialog
      .open(OpCaseSheetDialogComponent, {
        width: '820px',
        maxWidth: '95vw',
        data: { appointmentId: entry.appointmentId, directBillingId: entry.directBillingId }
      })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.refreshWorklist();
        }
      });
  }

  viewCaseSheet(entry: PrescriptionWorklistEntry): void {
    if (entry.appointmentId === null && entry.directBillingId === null) {
      return;
    }
    this.dialog.open(OpCaseSheetViewDialogComponent, {
      width: '760px',
      maxWidth: '95vw',
      data: { appointmentId: entry.appointmentId, directBillingId: entry.directBillingId }
    });
  }

}
