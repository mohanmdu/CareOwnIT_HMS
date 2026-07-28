import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from '../../../shared/services/notification.service';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { Admission } from '../admissions/admission.model';
import { AdmissionService } from '../admissions/admission.service';
import { PAYMENT_REQUEST_TYPE_OPTIONS, PaymentRequestType, requestTypeLabel } from './payment-request.model';
import { PaymentRequestService } from './payment-request.service';

/** Patient IP Details request form (Cashier Approval Workflow, PDF screen 1) - reached from the billing page's "Amount Received" action. */
@Component({
  selector: 'app-payment-request-form',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule, MatSelectModule, PageHeaderComponent],
  templateUrl: './payment-request-form.component.html',
  styleUrl: './payment-request-form.component.scss'
})
export class PaymentRequestFormComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly admissionService = inject(AdmissionService);
  private readonly paymentRequestService = inject(PaymentRequestService);
  private readonly notification = inject(NotificationService);

  private readonly admissionId = Number(this.route.snapshot.paramMap.get('id'));
  readonly requestTypeOptions = PAYMENT_REQUEST_TYPE_OPTIONS;

  admission = signal<Admission | null>(null);
  loading = signal(true);
  submitting = signal(false);

  form = {
    amount: null as number | null,
    description: '',
    requestType: 'ADVANCE' as PaymentRequestType
  };

  constructor() {
    this.admissionService.get(this.admissionId).subscribe({
      next: (admission) => {
        this.admission.set(admission);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load the admission.');
      }
    });
  }

  submit(): void {
    if (!this.form.amount || this.form.amount <= 0 || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.paymentRequestService
      .create(this.admissionId, this.form.requestType, this.form.amount, this.form.description.trim())
      .subscribe({
        next: (request) => {
          this.submitting.set(false);
          this.notification.success(`${requestTypeLabel(request.requestType)} request has been sent to cashier.`);
          this.router.navigate(['/ip/admissions', this.admissionId, 'billing']);
        },
        error: (err) => {
          this.submitting.set(false);
          this.notification.error(err.error?.message ?? 'Failed to submit the payment request.');
        }
      });
  }
}
