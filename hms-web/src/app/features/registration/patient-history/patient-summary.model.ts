import type { Admission } from '../../ip-admission/admissions/admission.model';
import type { Appointment } from '../../appointments/booking/appointment.model';
import type { LabRequisition } from '../../lab/requisitions/lab-requisition.model';
import type { PatientReport } from '../../patient-reports/patient-report.model';
import type { Patient } from '../patients/patient.model';

export type BillingSource = 'OP_DIRECT_BILLING' | 'IP_BILLING';

/** Unifies the app's 2 separate OP/IP billing paths for display - see the backend's BillingSummaryItem for why this exists. */
export interface BillingSummaryItem {
  source: BillingSource;
  id: number;
  invoiceNumber: string | null;
  billedDate: string | null;
  invoicedAmount: number | null;
  paidAmount: number | null;
  discountAmount: number | null;
  dueAmount: number | null;
  cancelled: boolean;
  cancellationReason: string | null;
  /** True only for Appointment/OpDirectBilling-sourced cancellations - IP refund/credit-note isn't built yet. */
  refundEligible: boolean;
}

export interface PaymentSummaryItem {
  source: BillingSource;
  billingId: number;
  receiptNumber: string | null;
  paidAt: string | null;
  amount: number | null;
  paymentMode: string | null;
}

/** Response shape of GET /api/registration/patients/{id}/summary - one round trip for the whole Patient Information screen. */
export interface PatientSummary {
  patient: Patient;
  admissions: Admission[];
  appointments: Appointment[];
  investigations: LabRequisition[];
  billing: BillingSummaryItem[];
  payments: PaymentSummaryItem[];
  files: PatientReport[];
}
