import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ConsultantService } from '../../masters-admin/consultants/consultant.service';
import { DepartmentService } from '../../masters-admin/departments/department.service';
import { GeneralUserService } from '../../masters-admin/general-users/general-user.service';
import { NotificationService } from '../../../shared/services/notification.service';
import { TablePagination } from '../../../shared/table/table-pagination';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import {
  BillType,
  COLLECTION_TYPE_LABELS,
  CollectionReport,
  CollectionReportRow,
  CollectionReportTotals,
  CollectionReportUserSummary,
  CollectionType,
  PAYMENT_MODE_OPTIONS
} from './collection-report.model';
import { CollectionReportService } from './collection-report.service';
import { COLLECTION_REPORT_PRINT_STYLES } from './collection-report-print-styles';

function matches(value: string | null | undefined, term: string): boolean {
  const trimmed = term.trim().toLowerCase();
  return !trimmed || (value ?? '').toLowerCase().includes(trimmed);
}

function toIsoDate(date: Date): string {
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
  'Type',
  'Bill Type',
  'Patient Name',
  'Patient UHID',
  'Bill No',
  'Date',
  'Payment Mode',
  'Consultant',
  'Department',
  'Created By',
  'Invoiced Amount',
  'Discount Amount',
  'Doctor Referral Amount',
  'Collected Amount',
  'Refund Amount'
];

/**
 * Cashier Module's "Collection Report" - one screen aggregating every real
 * money-collection event across the app (Appointment fees, OP Direct
 * Billing, Lab/Investigation charges, IP Advance/Final Settlement/Due
 * collections, Pharmacy bills). Backed by one round trip to
 * CollectionReportService.search() rather than the several separate
 * per-domain collection reports already elsewhere in the app - this is
 * purely additive, those keep working as-is.
 *
 * Department/Consultant/Payment Mode/User filters are pushed to the
 * backend query; Patient/UHID/Bill Number free-text search, pagination,
 * and column sorting are client-side over the already-fetched result (see
 * the Cashier Collection Report plan) - added in the next step.
 */
@Component({
  selector: 'app-cashier-collection-report',
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
    MatSortModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent
  ],
  templateUrl: './collection-report.component.html',
  styleUrl: './collection-report.component.scss'
})
export class CollectionReportComponent {
  private readonly service = inject(CollectionReportService);
  private readonly departmentService = inject(DepartmentService);
  private readonly consultantService = inject(ConsultantService);
  private readonly generalUserService = inject(GeneralUserService);
  private readonly notification = inject(NotificationService);

  readonly collectionTypeLabels = COLLECTION_TYPE_LABELS;
  readonly collectionTypes = Object.keys(COLLECTION_TYPE_LABELS) as CollectionType[];
  readonly paymentModeOptions = PAYMENT_MODE_OPTIONS;

  readonly columns = [
    'collectionType',
    'billType',
    'patientName',
    'billNumber',
    'billedAt',
    'paymentMode',
    'consultantName',
    'departmentName',
    'billedBy',
    'invoiceAmount',
    'discountAmount',
    'doctorReferralAmount',
    'receiptAmount',
    'refundAmount'
  ];

  readonly userSummaryColumns = ['displayName', 'transactionCount', 'invoiceAmount', 'discountAmount', 'receiptAmount', 'refundAmount', 'netCollectionAmount'];

  fromDate = signal<Date>(new Date());
  toDate = signal<Date>(new Date());
  billType = signal<BillType | null>(null);
  collectionType = signal<CollectionType | null>(null);
  paymentMode = signal<string | null>(null);
  departmentId = signal<number | null>(null);
  consultantId = signal<number | null>(null);
  username = signal<string | null>(null);

  departments = signal<{ id: number; name: string }[]>([]);
  consultants = signal<{ id: number; name: string }[]>([]);
  users = signal<{ username: string; name: string }[]>([]);

  loading = signal(false);
  report = signal<CollectionReport | null>(null);

  // Patient/UHID and Bill Number are client-side-only filters over the already-fetched rows
  // (the backend filters above are dropdown-driven and pushed to SQL; these are free-text
  // refinements, same split as the Cashier Collection Report plan).
  patientSearch = signal('');
  billNumberSearch = signal('');
  sort = signal<Sort | null>(null);

  readonly pageSizeOptions = [10, 25, 50, 100];

  filteredRows = computed<CollectionReportRow[]>(() => {
    const rows = this.report()?.rows ?? [];
    const patientTerm = this.patientSearch();
    const billTerm = this.billNumberSearch();
    return rows.filter(
      (row) =>
        (matches(row.patientName, patientTerm) || matches(row.patientUhid, patientTerm)) && matches(row.billNumber, billTerm)
    );
  });

  // Recomputed client-side from filteredRows() (post patient/bill-number search) rather than
  // reused as-is from the backend's report().totals, so the footer always matches what the
  // Patient/UHID/Bill Number search has actually narrowed the table down to.
  displayedTotals = computed<CollectionReportTotals>(() => {
    const rows = this.filteredRows();
    const invoiceAmount = rows.reduce((sum, r) => sum + r.invoiceAmount, 0);
    const discountAmount = rows.reduce((sum, r) => sum + r.discountAmount, 0);
    const doctorReferralAmount = rows.reduce((sum, r) => sum + r.doctorReferralAmount, 0);
    const receiptAmount = rows.reduce((sum, r) => sum + r.receiptAmount, 0);
    const refundAmount = rows.reduce((sum, r) => sum + r.refundAmount, 0);
    return {
      invoiceAmount,
      discountAmount,
      doctorReferralAmount,
      receiptAmount,
      refundAmount,
      totalCollectionAmount: receiptAmount - refundAmount
    };
  });

  // Recomputed client-side from filteredRows(), same reasoning as displayedTotals() above -
  // stays in sync with the Patient/UHID/Bill Number search rather than reusing report().userSummary as-is.
  displayedUserSummary = computed<CollectionReportUserSummary[]>(() => {
    const byUser = new Map<string, CollectionReportRow[]>();
    for (const row of this.filteredRows()) {
      const key = row.billedByUsername ?? '';
      const bucket = byUser.get(key);
      if (bucket) {
        bucket.push(row);
      } else {
        byUser.set(key, [row]);
      }
    }
    const summary: CollectionReportUserSummary[] = [];
    for (const [key, rows] of byUser) {
      const invoiceAmount = rows.reduce((sum, r) => sum + r.invoiceAmount, 0);
      const discountAmount = rows.reduce((sum, r) => sum + r.discountAmount, 0);
      const receiptAmount = rows.reduce((sum, r) => sum + r.receiptAmount, 0);
      const refundAmount = rows.reduce((sum, r) => sum + r.refundAmount, 0);
      summary.push({
        username: key === '' ? null : key,
        displayName: rows[0].billedByDisplayName,
        transactionCount: rows.length,
        invoiceAmount,
        discountAmount,
        receiptAmount,
        refundAmount,
        netCollectionAmount: receiptAmount - refundAmount
      });
    }
    return summary.sort((a, b) => b.netCollectionAmount - a.netCollectionAmount);
  });

  sortedRows = computed<CollectionReportRow[]>(() => {
    const rows = this.filteredRows();
    const active = this.sort();
    if (!active || !active.direction) {
      return rows;
    }
    const factor = active.direction === 'asc' ? 1 : -1;
    const field = active.active as keyof CollectionReportRow;
    return [...rows].sort((a, b) => {
      const left = a[field];
      const right = b[field];
      if (left == null && right == null) {
        return 0;
      }
      if (left == null) {
        return -1 * factor;
      }
      if (right == null) {
        return 1 * factor;
      }
      if (typeof left === 'number' && typeof right === 'number') {
        return (left - right) * factor;
      }
      return String(left).localeCompare(String(right)) * factor;
    });
  });

  pagination = new TablePagination(this.sortedRows);
  totalPages = computed(() => Math.max(Math.ceil(this.pagination.length() / this.pagination.pageSize()), 1));
  pageNumbers = computed(() => Array.from({ length: this.totalPages() }, (_, i) => i + 1));
  rangeStart = computed(() => (this.pagination.length() === 0 ? 0 : this.pagination.pageIndex() * this.pagination.pageSize() + 1));
  rangeEnd = computed(() => Math.min((this.pagination.pageIndex() + 1) * this.pagination.pageSize(), this.pagination.length()));

  constructor() {
    this.departmentService.list().subscribe({
      next: (departments) => this.departments.set(departments.map((d) => ({ id: d.id!, name: d.name }))),
      error: () => this.notification.error('Failed to load departments.')
    });
    this.consultantService.list().subscribe({
      next: (consultants) => this.consultants.set(consultants.map((c) => ({ id: c.id!, name: c.name }))),
      error: () => this.notification.error('Failed to load consultants.')
    });
    this.generalUserService.list().subscribe({
      next: (users) => this.users.set(users.map((u) => ({ username: u.username, name: u.name }))),
      error: () => this.notification.error('Failed to load users.')
    });

    this.search();
  }

  search(): void {
    this.loading.set(true);
    this.service
      .search({
        from: toIsoDate(this.fromDate()),
        to: toIsoDate(this.toDate()),
        billType: this.billType() ?? undefined,
        collectionType: this.collectionType() ?? undefined,
        paymentMode: this.paymentMode() ?? undefined,
        departmentId: this.departmentId() ?? undefined,
        consultantId: this.consultantId() ?? undefined,
        username: this.username() ?? undefined
      })
      .subscribe({
        next: (report) => {
          this.report.set(report);
          this.loading.set(false);
          this.pagination.reset();
        },
        error: () => {
          this.loading.set(false);
          this.notification.error('Failed to load the collection report.');
        }
      });
  }

  resetFilters(): void {
    this.billType.set(null);
    this.collectionType.set(null);
    this.paymentMode.set(null);
    this.departmentId.set(null);
    this.consultantId.set(null);
    this.username.set(null);
    this.patientSearch.set('');
    this.billNumberSearch.set('');
    this.search();
  }

  onPatientSearchChange(value: string): void {
    this.patientSearch.set(value);
    this.pagination.reset();
  }

  onBillNumberSearchChange(value: string): void {
    this.billNumberSearch.set(value);
    this.pagination.reset();
  }

  onSortChange(sort: Sort): void {
    this.sort.set(sort);
  }

  onPageSizeChange(size: number): void {
    this.pagination.pageSize.set(size);
    this.pagination.reset();
  }

  goToPage(page: number): void {
    this.pagination.pageIndex.set(page - 1);
  }

  previousPage(): void {
    this.pagination.pageIndex.update((i) => Math.max(i - 1, 0));
  }

  nextPage(): void {
    this.pagination.pageIndex.update((i) => Math.min(i + 1, this.totalPages() - 1));
  }

  /**
   * Standalone popup print, same overall pattern as the Appointments
   * Collection Report/Refund Report - built from sortedRows() directly
   * (rather than capturing the on-screen table's innerHTML like those
   * screens do) since this report, unlike those, paginates the on-screen
   * table; printing must still cover the whole filtered+sorted result, not
   * just the current page.
   */
  print(): void {
    const rows = this.sortedRows();
    if (rows.length === 0) {
      return;
    }
    const printWindow = window.open('', '_blank', 'width=1100,height=900');
    if (!printWindow) {
      this.notification.error('Please allow pop-ups to print the report.');
      return;
    }
    const totals = this.displayedTotals();
    const tableRows = rows
      .map(
        (row) => `
          <tr>
            <td>${this.collectionTypeLabels[row.collectionType]}</td>
            <td>${row.billType}</td>
            <td>${row.patientName} (${row.patientUhid})</td>
            <td>${row.billNumber ?? '-'}</td>
            <td>${row.billedAt ?? '-'}</td>
            <td>${row.paymentMode ?? '-'}</td>
            <td>${row.consultantName ?? '-'}</td>
            <td>${row.departmentName ?? '-'}</td>
            <td>${row.billedByDisplayName ?? row.billedByUsername ?? '-'}</td>
            <td>${row.invoiceAmount.toFixed(2)}</td>
            <td>${row.discountAmount.toFixed(2)}</td>
            <td>${row.doctorReferralAmount.toFixed(2)}</td>
            <td>${row.receiptAmount.toFixed(2)}</td>
            <td>${row.refundAmount.toFixed(2)}</td>
          </tr>`
      )
      .join('');
    printWindow.document.write(`
      <html>
        <head>
          <title>Collection Report</title>
          <style>${COLLECTION_REPORT_PRINT_STYLES}</style>
        </head>
        <body>
          <h1>Collection Report</h1>
          <table>
            <thead>
              <tr>
                <th>Type</th><th>Bill Type</th><th>Patient</th><th>Bill #</th><th>Date</th><th>Mode</th>
                <th>Consultant</th><th>Department</th><th>Created By</th><th>Invoiced</th><th>Discount</th>
                <th>Doctor Referral</th><th>Collected</th><th>Refund</th>
              </tr>
            </thead>
            <tbody>${tableRows}</tbody>
          </table>
          <div class="collection-report-totals">
            <span>Transactions: <strong>${rows.length}</strong></span>
            <span>Invoiced: <strong>${totals.invoiceAmount.toFixed(2)}</strong></span>
            <span>Discount: <strong>${totals.discountAmount.toFixed(2)}</strong></span>
            <span>Doctor Referral: <strong>${totals.doctorReferralAmount.toFixed(2)}</strong></span>
            <span>Collected: <strong>${totals.receiptAmount.toFixed(2)}</strong></span>
            <span>Refund: <strong>${totals.refundAmount.toFixed(2)}</strong></span>
            <span>Net Collection: <strong>${totals.totalCollectionAmount.toFixed(2)}</strong></span>
          </div>
        </body>
      </html>
    `);
    printWindow.document.close();
    printWindow.focus();
    printWindow.onload = () => printWindow.print();
  }

  /** Exports the full filtered+sorted result set, not just the current page. */
  export(): void {
    const rows = this.sortedRows();
    if (rows.length === 0) {
      return;
    }
    const lines = [CSV_HEADERS.map(csvCell).join(',')];
    rows.forEach((row, index) => {
      lines.push(
        [
          csvCell(index + 1),
          csvCell(this.collectionTypeLabels[row.collectionType]),
          csvCell(row.billType),
          csvCell(row.patientName),
          csvCell(row.patientUhid),
          csvCell(row.billNumber),
          csvCell(row.billedAt),
          csvCell(row.paymentMode),
          csvCell(row.consultantName),
          csvCell(row.departmentName),
          csvCell(row.billedByDisplayName ?? row.billedByUsername),
          csvCell(row.invoiceAmount),
          csvCell(row.discountAmount),
          csvCell(row.doctorReferralAmount),
          csvCell(row.receiptAmount),
          csvCell(row.refundAmount)
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
