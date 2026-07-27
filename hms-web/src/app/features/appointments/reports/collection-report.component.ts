import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { NotificationService } from '../../../shared/services/notification.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge/status-badge.component';
import { Consultant } from '../../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { OpDirectBillingReceiptDialogComponent } from '../../direct-billing/op-direct-billing-receipt-dialog.component';
import { OpDirectBillingService } from '../../direct-billing/op-direct-billing.service';
import { AppointmentBillingDialogComponent } from '../booking/appointment-billing-dialog.component';
import {
  CollectionReportEntry,
  CollectionReportSource,
  PAYMENT_MODE_LABELS,
  PAYMENT_MODES,
  PaymentMode
} from '../booking/appointment.model';
import { AppointmentService } from '../booking/appointment.service';
import { REPORT_PRINT_STYLES } from './report-print-styles';

const CATEGORY_LABELS: Record<CollectionReportSource, string> = {
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

function csvCell(value: string | number | null | undefined): string {
  const text = value === null || value === undefined ? '' : String(value);
  return `"${text.replace(/"/g, '""')}"`;
}

const CSV_HEADERS = [
  'S.No',
  'Patient Name',
  'Patient ID',
  'Category',
  'Date & Time',
  'Payment Mode',
  'Consultant Name',
  'User Name',
  'Invoice No',
  'Invoice Amount',
  'Doctor Referral Amount',
  'Receipt Amount',
  'Refund Amount'
];

/**
 * Doctor/receptionist-facing view of every billed appointment (status =
 * COMPLETED), keyed off the Invoice No stamped by AppointmentService.bill().
 * Mirrors the legacy Collection Report screenshot's columns, but the Total
 * Collection Amount here is computed as paidAmount - refundAmount rather
 * than reproducing the legacy total, which didn't reconcile with its own
 * line items.
 */
@Component({
  selector: 'app-collection-report',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusBadgeComponent
  ],
  templateUrl: './collection-report.component.html',
  styleUrl: './collection-report.component.scss'
})
export class CollectionReportComponent {
  private readonly service = inject(AppointmentService);
  private readonly consultantService = inject(ConsultantService);
  private readonly directBillingService = inject(OpDirectBillingService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  @ViewChild('reportContent') reportContent?: ElementRef<HTMLElement>;

  readonly displayedColumns = [
    'serial',
    'patientName',
    'patientId',
    'category',
    'billedAt',
    'paymentMode',
    'consultantName',
    'billedBy',
    'invoiceNumber',
    'invoicedAmount',
    'doctorReferralAmount',
    'paidAmount',
    'refundAmount'
  ];
  readonly paymentModes = PAYMENT_MODES;
  readonly paymentModeLabels = PAYMENT_MODE_LABELS;

  categoryLabel(source: CollectionReportSource): string {
    return CATEGORY_LABELS[source];
  }

  consultants = signal<Consultant[]>([]);
  entries = signal<CollectionReportEntry[]>([]);
  loading = signal(false);
  searched = signal(false);

  fromDate = signal<Date | null>(new Date());
  toDate = signal<Date | null>(new Date());
  consultantId = signal<number | null>(null);
  paymentMode = signal<PaymentMode | null>(null);

  paymentModeLabel(mode: PaymentMode | null): string {
    return mode ? this.paymentModeLabels[mode] : '—';
  }

  totals = computed(() => {
    const rows = this.entries();
    const invoicedAmount = rows.reduce((sum, r) => sum + (r.invoicedAmount ?? 0), 0);
    const doctorReferralAmount = rows.reduce((sum, r) => sum + (r.doctorReferralAmount ?? 0), 0);
    const paidAmount = rows.reduce((sum, r) => sum + (r.paidAmount ?? 0), 0);
    const refundAmount = rows.reduce((sum, r) => sum + (r.refundAmount ?? 0), 0);
    return {
      invoicedAmount,
      doctorReferralAmount,
      paidAmount,
      refundAmount,
      totalCollection: paidAmount - refundAmount
    };
  });

  constructor() {
    this.consultantService.list().subscribe({
      next: (consultants) => this.consultants.set(consultants),
      error: () => this.notification.error('Failed to load consultants.')
    });
    this.search();
  }

  search(): void {
    this.loading.set(true);
    this.service
      .getCollectionReport({
        fromDate: toIsoDate(this.fromDate()),
        toDate: toIsoDate(this.toDate()),
        consultantId: this.consultantId() ?? undefined,
        paymentMode: this.paymentMode() ?? undefined
      })
      .subscribe({
        next: (entries) => {
          this.entries.set(entries);
          this.loading.set(false);
          this.searched.set(true);
        },
        error: () => {
          this.loading.set(false);
          this.notification.error('Failed to load collection report.');
        }
      });
  }

  /** Reopens the receipt view for this row's invoice - branches by source since Appointment and OP Direct Billing use different receipt dialogs. */
  viewReceipt(entry: CollectionReportEntry): void {
    if (entry.source === 'DIRECT_BILLING') {
      if (entry.directBillingId === null) {
        return;
      }
      this.directBillingService.get(entry.directBillingId).subscribe({
        next: (receipt) => {
          this.dialog.open(OpDirectBillingReceiptDialogComponent, {
            width: '640px',
            maxWidth: '95vw',
            data: { receipt }
          });
        },
        error: () => this.notification.error('Failed to load the receipt for this invoice.')
      });
      return;
    }
    if (entry.appointmentId === null) {
      return;
    }
    this.service.get(entry.appointmentId).subscribe({
      next: (appointment) => {
        this.dialog.open(AppointmentBillingDialogComponent, {
          width: '640px',
          maxWidth: '95vw',
          data: { appointment }
        });
      },
      error: () => this.notification.error('Failed to load the receipt for this invoice.')
    });
  }

  /** Standalone popup print, same reasoning as AppointmentBillingDialogComponent.print(). */
  print(): void {
    const content = this.reportContent?.nativeElement.innerHTML;
    if (!content) {
      return;
    }
    const printWindow = window.open('', '_blank', 'width=1000,height=900');
    if (!printWindow) {
      this.notification.error('Please allow pop-ups to print the report.');
      return;
    }
    printWindow.document.write(`
      <html>
        <head>
          <title>Collection Report</title>
          <style>${REPORT_PRINT_STYLES}</style>
        </head>
        <body>
          <h1>Collection Report</h1>
          ${content}
        </body>
      </html>
    `);
    printWindow.document.close();
    printWindow.focus();
    printWindow.onload = () => printWindow.print();
  }

  export(): void {
    const rows = this.entries();
    if (rows.length === 0) {
      return;
    }
    const lines = [CSV_HEADERS.map(csvCell).join(',')];
    rows.forEach((entry, index) => {
      lines.push(
        [
          csvCell(index + 1),
          csvCell(entry.patientName),
          csvCell(entry.patientRegistrationNumber),
          csvCell(CATEGORY_LABELS[entry.source]),
          csvCell(entry.billedAt),
          csvCell(this.paymentModeLabel(entry.paymentMode)),
          csvCell(entry.consultantName ?? '-'),
          csvCell(entry.billedBy),
          csvCell(entry.invoiceNumber),
          csvCell(entry.invoicedAmount),
          csvCell(entry.doctorReferralAmount),
          csvCell(entry.paidAmount),
          csvCell(entry.refundAmount)
        ].join(',')
      );
    });
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `collection-report-${toIsoDate(new Date())}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }
}
