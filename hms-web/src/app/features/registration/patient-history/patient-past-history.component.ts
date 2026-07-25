import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { Router, RouterLink } from '@angular/router';
import { NotificationService } from '../../../shared/services/notification.service';
import { PromptDialogService } from '../../../shared/services/prompt-dialog.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent, StatusBadgeTone } from '../../../shared/ui/status-badge/status-badge.component';
import { PatientSearchComponent } from '../../../shared/ui/patient-search/patient-search.component';
import { Appointment } from '../../appointments/booking/appointment.model';
import { OpCaseSheetViewDialogComponent } from '../../appointments/prescriptions/op-case-sheet-view-dialog.component';
import { LabRequisition } from '../../lab/requisitions/lab-requisition.model';
import { PatientReport } from '../../patient-reports/patient-report.model';
import { PatientReportService } from '../../patient-reports/patient-report.service';
import { Patient } from '../patients/patient.model';
import { PatientService } from '../patients/patient.service';
import { PatientSummary } from './patient-summary.model';

const APPOINTMENT_STATUS_TONE: Record<string, StatusBadgeTone> = {
  BOOKED: 'warning',
  CONFIRMED: 'info',
  CANCELLED: 'danger',
  COMPLETED: 'success'
};

const ADMISSION_STATUS_TONE: Record<string, StatusBadgeTone> = {
  REGISTERED: 'warning',
  ADMITTED: 'info',
  DISCHARGE_INITIATED: 'warning',
  DISCHARGED: 'success',
  CANCELLED: 'danger'
};

const INVESTIGATION_STATUS_TONE: Record<string, StatusBadgeTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  CANCELLED: 'danger'
};

const BILLING_SOURCE_LABEL: Record<string, string> = {
  OP_INVOICE: 'OP Invoice',
  OP_DIRECT_BILLING: 'OP Direct Billing',
  IP_BILLING: 'IP Billing'
};

/**
 * Cancelled Details is a pure client-side view over the already-fetched
 * summary (appointments/investigations/billing each already carry their own
 * cancellation state) - deliberately not a 4th backend DTO, see the Patient
 * Information plan's decision on this. refundLink is null when nothing can
 * be refunded from this row (IP-sourced billing, an investigation that was
 * cancelled before ever being billed, or a billing row that's already been
 * refunded) - see RefundService/LabRefundService's "already refunded" guards.
 */
interface CancelledRow {
  type: string;
  description: string;
  date: string | null;
  amount: number | null;
  reason: string | null;
  refundLink: string | null;
  invoiceNumber: number | null;
}

/**
 * Patient Information (OP/IP) - consolidated patient record (migration doc's
 * Patient Past History module, extended into the full workflow: demographics
 * + photo, and a tab per domain - Inpatient Details, Appointments,
 * Investigations Done, Billing Details, Payments Details, Cancelled
 * Details, Patient Files). Search-then-reveal, same pattern as OP Direct
 * Billing/the booking wizard's patient step, backed by one round trip to
 * PatientService.summary() rather than a call per tab.
 *
 * Built incrementally - tabs land as their own backend support does (see
 * the Patient Information plan); Appointments is populated from day one
 * since AppointmentService already supported a patientId-scoped query.
 */
@Component({
  selector: 'app-patient-past-history',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTabsModule,
    RouterLink,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusBadgeComponent,
    PatientSearchComponent
  ],
  templateUrl: './patient-past-history.component.html',
  styleUrl: './patient-past-history.component.scss'
})
export class PatientPastHistoryComponent {
  private readonly patientService = inject(PatientService);
  private readonly patientReportService = inject(PatientReportService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly promptDialog = inject(PromptDialogService);
  private readonly router = inject(Router);

  readonly appointmentColumns = ['appointmentDate', 'consultant', 'department', 'status', 'actions'];
  readonly appointmentStatusTone = APPOINTMENT_STATUS_TONE;

  readonly admissionColumns = ['admissionNumber', 'admissionDate', 'room', 'status', 'advance', 'totalBilled', 'settlement'];
  readonly admissionStatusTone = ADMISSION_STATUS_TONE;

  readonly investigationColumns = ['requisitionDate', 'requisitionNumber', 'tests', 'amount', 'status'];
  readonly investigationStatusTone = INVESTIGATION_STATUS_TONE;

  readonly billingColumns = ['source', 'invoiceNumber', 'billedDate', 'invoiced', 'paid', 'due', 'status'];
  readonly paymentColumns = ['source', 'receiptNumber', 'paidAt', 'amount', 'paymentMode'];
  readonly billingSourceLabel = BILLING_SOURCE_LABEL;

  readonly cancelledColumns = ['type', 'description', 'date', 'amount', 'reason', 'actions'];

  readonly fileColumns = ['fileType', 'originalFileName', 'uploadedAt', 'uploadedBy', 'comments', 'actions'];

  patient = signal<Patient | null>(null);
  summary = signal<PatientSummary | null>(null);
  loading = signal(false);
  uploadingPhoto = signal(false);
  uploadingFile = signal(false);

  cancelledRows = computed<CancelledRow[]>(() => {
    const data = this.summary();
    if (!data) {
      return [];
    }

    const rows: CancelledRow[] = [];

    for (const appointment of data.appointments) {
      if (appointment.status !== 'CANCELLED') {
        continue;
      }
      const refundable = (appointment.paidAmount ?? 0) > 0 && appointment.invoiceNumber != null && appointment.refundAmount == null;
      rows.push({
        type: 'Appointment',
        description: `${appointment.consultantName ?? 'Consultant'} - ${appointment.appointmentDate}`,
        date: appointment.appointmentDate,
        amount: appointment.paidAmount,
        reason: appointment.cancellationReason,
        refundLink: refundable ? '/appointments/refunds' : null,
        invoiceNumber: appointment.invoiceNumber
      });
    }

    for (const investigation of data.investigations) {
      if (investigation.status !== 'CANCELLED') {
        continue;
      }
      const refundable = (investigation.paidAmount ?? 0) > 0 && investigation.invoiceNumber != null;
      rows.push({
        type: 'Investigation',
        description: `${investigation.requisitionNumber} - ${this.testNames(investigation)}`,
        date: investigation.requisitionDate,
        amount: investigation.totalAmount,
        reason: null,
        refundLink: refundable ? '/lab/refunds' : null,
        invoiceNumber: investigation.invoiceNumber
      });
    }

    for (const bill of data.billing) {
      if (!bill.cancelled) {
        continue;
      }
      rows.push({
        type: this.billingSourceLabel[bill.source] ?? bill.source,
        description: bill.invoiceNumber ? `Invoice #${bill.invoiceNumber}` : 'Billing',
        date: bill.billedDate,
        amount: bill.invoicedAmount,
        reason: bill.cancellationReason,
        refundLink: null,
        invoiceNumber: null
      });
    }

    return rows;
  });

  selectPatient(patient: Patient): void {
    if (!patient.id) {
      return;
    }
    this.loadSummary(patient.id);
  }

  reset(): void {
    this.patient.set(null);
    this.summary.set(null);
  }

  private loadSummary(id: number): void {
    this.loading.set(true);
    this.patientService.summary(id).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.patient.set(summary.patient);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load patient information.');
      }
    });
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const patientId = this.patient()?.id;
    input.value = '';
    if (!file || !patientId) {
      return;
    }
    this.uploadingPhoto.set(true);
    this.patientService.uploadPhoto(patientId, file).subscribe({
      next: (updated) => {
        this.uploadingPhoto.set(false);
        this.patient.set(updated);
        this.notification.success('Photo updated.');
      },
      error: () => {
        this.uploadingPhoto.set(false);
        this.notification.error('Failed to upload photo.');
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const patientId = this.patient()?.id;
    input.value = '';
    if (!file || !patientId) {
      return;
    }
    this.uploadingFile.set(true);
    this.patientReportService.upload(patientId, null, file).subscribe({
      next: () => {
        this.uploadingFile.set(false);
        this.notification.success('File uploaded.');
        this.loadSummary(patientId);
      },
      error: () => {
        this.uploadingFile.set(false);
        this.notification.error('Failed to upload the file.');
      }
    });
  }

  deleteFile(file: PatientReport): void {
    const patientId = this.patient()?.id;
    if (!patientId) {
      return;
    }
    this.promptDialog
      .prompt({
        title: 'Delete this file?',
        message: `"${file.originalFileName}" will move to the Deleted Files list.`,
        fields: [{ key: 'reason', label: 'Reason', type: 'textarea', required: true }],
        confirmLabel: 'Delete',
        destructive: true
      })
      .subscribe((values) => {
        if (!values) {
          return;
        }
        this.patientReportService.softDelete(file.id, values['reason'] as string).subscribe({
          next: () => {
            this.notification.success('File deleted.');
            this.loadSummary(patientId);
          },
          error: () => this.notification.error('Failed to delete the file.')
        });
      });
  }

  testNames(requisition: LabRequisition): string {
    return requisition.items.map((item) => item.subCategoryName).join(', ') || '-';
  }

  goToRefund(row: CancelledRow): void {
    if (!row.refundLink || row.invoiceNumber == null) {
      return;
    }
    this.router.navigate([row.refundLink], { queryParams: { invoiceNumber: row.invoiceNumber } });
  }

  viewAppointmentDetails(appointment: Appointment): void {
    if (appointment.id === null) {
      return;
    }
    this.dialog.open(OpCaseSheetViewDialogComponent, {
      width: '760px',
      maxWidth: '95vw',
      data: { appointmentId: appointment.id }
    });
  }
}
