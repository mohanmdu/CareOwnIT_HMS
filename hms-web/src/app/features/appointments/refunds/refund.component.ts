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
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute } from '@angular/router';
import { NotificationService } from '../../../shared/services/notification.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { Consultant } from '../../masters-admin/consultants/consultant.model';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { RefundReceiptDialogComponent } from './refund-receipt-dialog.component';
import { RefundCandidate, RefundReceiptEntry } from './refund.model';
import { REFUND_REPORT_PRINT_STYLES } from './refund-report-print-styles';
import { RefundService } from './refund.service';

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
  'Refund No',
  'Patient ID',
  'Patient Name',
  'Type',
  'Invoice No',
  'Invoice Amount',
  'Paid Amount',
  'Refund Amount',
  'Reason',
  'Refund Date',
  'Refunded By'
];

/**
 * One screen, two tabs (Payment Refund / Refund Report) - same shape as
 * PatientRegistrationComponent's Active/Inactive tabs: the report tab is
 * lazy-loaded the first time it's switched to (and refreshed on every
 * subsequent switch), rather than being a separate routed page.
 */
@Component({
  selector: 'app-refund',
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
    MatTabsModule,
    PageHeaderComponent,
    EmptyStateComponent
  ],
  templateUrl: './refund.component.html',
  styleUrl: './refund.component.scss'
})
export class RefundComponent {
  private readonly service = inject(RefundService);
  private readonly consultantService = inject(ConsultantService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);

  @ViewChild('reportContent') reportContent?: ElementRef<HTMLElement>;

  // --- Payment Refund tab ---
  invoiceNumberInput: number | null = null;
  searching = signal(false);
  candidate = signal<RefundCandidate | null>(null);
  formVisible = signal(false);
  confirming = signal(false);

  form = {
    refundAmount: 0,
    reason: ''
  };

  get isValid(): boolean {
    const paidAmount = this.candidate()?.paidAmount ?? 0;
    return this.form.refundAmount > 0 && this.form.refundAmount <= paidAmount;
  }

  // --- Refund Report tab ---
  readonly displayedColumns = [
    'serial',
    'refundNumber',
    'patientId',
    'patientName',
    'type',
    'invoiceNumber',
    'invoicedAmount',
    'paidAmount',
    'refundAmount',
    'reason',
    'refundedAt',
    'refundedBy'
  ];

  consultants = signal<Consultant[]>([]);
  entries = signal<RefundReceiptEntry[]>([]);
  loadingReport = signal(false);
  reportSearched = signal(false);

  fromDate = signal<Date | null>(new Date());
  toDate = signal<Date | null>(new Date());
  consultantId = signal<number | null>(null);

  totalRefundAmount = computed(() => this.entries().reduce((sum, r) => sum + r.refundAmount, 0));

  constructor() {
    this.consultantService.list().subscribe({
      next: (consultants) => this.consultants.set(consultants),
      error: () => this.notification.error('Failed to load consultants.')
    });

    // Deep-linked from the Patient Information (OP/IP) screen's Cancelled Details tab.
    const invoiceNumber = Number(this.route.snapshot.queryParamMap.get('invoiceNumber'));
    if (invoiceNumber > 0) {
      this.invoiceNumberInput = invoiceNumber;
      this.searchInvoice();
    }
  }

  onTabChange(index: number): void {
    if (index === 1) {
      this.searchReport();
    }
  }

  searchInvoice(): void {
    if (!this.invoiceNumberInput) {
      return;
    }
    this.searching.set(true);
    this.candidate.set(null);
    this.formVisible.set(false);
    this.service.search(this.invoiceNumberInput).subscribe({
      next: (candidate) => {
        this.searching.set(false);
        this.candidate.set(candidate);
      },
      error: (err) => {
        this.searching.set(false);
        this.notification.error(err.error?.message ?? 'No billed invoice found for that Invoice No.');
      }
    });
  }

  approveRefund(): void {
    const candidate = this.candidate();
    if (!candidate) {
      return;
    }
    this.form.refundAmount = candidate.paidAmount ?? 0;
    this.form.reason = '';
    this.formVisible.set(true);
  }

  cancelForm(): void {
    this.formVisible.set(false);
  }

  confirm(): void {
    const candidate = this.candidate();
    if (!candidate || !this.isValid) {
      return;
    }
    this.confirming.set(true);
    this.service
      .create({
        sourceId: candidate.sourceId,
        source: candidate.source,
        refundAmount: this.form.refundAmount,
        reason: this.form.reason.trim() || null
      })
      .subscribe({
        next: (refund) => {
          this.confirming.set(false);
          this.dialog.open(RefundReceiptDialogComponent, {
            width: '640px',
            maxWidth: '95vw',
            data: { refund }
          });
          this.newSearch();
        },
        error: (err) => {
          this.confirming.set(false);
          this.notification.error(err.error?.message ?? 'Failed to process the refund.');
        }
      });
  }

  newSearch(): void {
    this.invoiceNumberInput = null;
    this.candidate.set(null);
    this.formVisible.set(false);
  }

  searchReport(): void {
    this.loadingReport.set(true);
    this.service
      .getReport({
        fromDate: toIsoDate(this.fromDate()),
        toDate: toIsoDate(this.toDate()),
        consultantId: this.consultantId() ?? undefined
      })
      .subscribe({
        next: (entries) => {
          this.entries.set(entries);
          this.loadingReport.set(false);
          this.reportSearched.set(true);
        },
        error: () => {
          this.loadingReport.set(false);
          this.notification.error('Failed to load the refund report.');
        }
      });
  }

  /** Reopens the refund receipt for this row - no extra API call, the row already has every field the receipt needs. */
  viewReceipt(entry: RefundReceiptEntry): void {
    this.dialog.open(RefundReceiptDialogComponent, {
      width: '640px',
      maxWidth: '95vw',
      data: { refund: entry }
    });
  }

  /** Standalone popup print, same pattern as CollectionReportComponent.print(). */
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
          <title>Refund Report</title>
          <style>${REFUND_REPORT_PRINT_STYLES}</style>
        </head>
        <body>
          <h1>Refund Report</h1>
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
          csvCell(entry.refundNumber),
          csvCell(entry.patientRegistrationNumber),
          csvCell(entry.patientName),
          csvCell(entry.type),
          csvCell(entry.invoiceNumber),
          csvCell(entry.invoicedAmount),
          csvCell(entry.paidAmount),
          csvCell(entry.refundAmount),
          csvCell(entry.reason),
          csvCell(entry.refundedAt),
          csvCell(entry.refundedBy)
        ].join(',')
      );
    });
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `refund-report-${toIsoDate(new Date())}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }
}
