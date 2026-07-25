import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from '../../../shared/services/notification.service';
import { LabRefundCandidate } from './lab-refund.model';
import { LabRefundService } from './lab-refund.service';

/**
 * Payment Refund (screens 1-2 of the refund flow): search a billed invoice
 * by Invoice No, review its details, and approve a refund - all inline
 * state transitions on one page (mirrors the Payment Refund tab of
 * com.pms.registration's RefundComponent). Confirming routes to a full
 * read-only receipt page (../requisitions/lab-receipt.component's
 * convention), not a MatDialog.
 */
@Component({
  selector: 'app-lab-refund',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  templateUrl: './lab-refund.component.html',
  styleUrl: './lab-refund.component.scss'
})
export class LabRefundComponent {
  private readonly service = inject(LabRefundService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  invoiceNumberInput: number | null = null;
  searching = signal(false);
  candidate = signal<LabRefundCandidate | null>(null);

  formVisible = signal(false);
  confirming = signal(false);
  form: { refundAmount: number | null; reason: string } = { refundAmount: null, reason: '' };

  constructor() {
    // Deep-linked from the Patient Information (OP/IP) screen's Cancelled Details tab.
    const invoiceNumber = Number(this.route.snapshot.queryParamMap.get('invoiceNumber'));
    if (invoiceNumber > 0) {
      this.invoiceNumberInput = invoiceNumber;
      this.searchInvoice();
    }
  }

  get isValid(): boolean {
    const paid = this.candidate()?.paidAmount ?? 0;
    return this.form.refundAmount !== null && this.form.refundAmount > 0 && this.form.refundAmount <= paid;
  }

  searchInvoice(): void {
    if (!this.invoiceNumberInput) {
      return;
    }
    this.searching.set(true);
    this.formVisible.set(false);
    this.service.search(this.invoiceNumberInput).subscribe({
      next: (candidate) => {
        this.candidate.set(candidate);
        this.searching.set(false);
      },
      error: (err) => {
        this.candidate.set(null);
        this.searching.set(false);
        this.notification.error(err.error?.message ?? 'No billed invoice found for that Invoice No.');
      }
    });
  }

  approveRefund(): void {
    this.form = { refundAmount: this.candidate()?.paidAmount ?? null, reason: '' };
    this.formVisible.set(true);
  }

  cancelForm(): void {
    this.formVisible.set(false);
  }

  confirm(): void {
    const candidate = this.candidate();
    if (!candidate || !this.isValid || this.form.refundAmount === null) {
      return;
    }
    this.confirming.set(true);
    this.service
      .create({ requisitionId: candidate.requisitionId, refundAmount: this.form.refundAmount, reason: this.form.reason || null })
      .subscribe({
        next: (refund) => {
          this.confirming.set(false);
          this.router.navigate(['/lab/refunds', refund.id, 'receipt']);
        },
        error: (err) => {
          this.confirming.set(false);
          this.notification.error(err.error?.message ?? 'Failed to process the refund.');
        }
      });
  }
}
