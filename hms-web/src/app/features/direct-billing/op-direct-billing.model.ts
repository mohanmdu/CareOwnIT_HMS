import { PaymentMode } from '../appointments/booking/appointment.model';

/** A line already added to the working bill, before submit. */
export interface OpDirectBillingWorkingItem {
  componentId: number;
  categoryName: string;
  componentName: string;
  quantity: number;
  amount: number;
  remarks: string | null;
}

export interface OpDirectBillingItemRequest {
  componentId: number;
  quantity: number;
  amount: number;
  remarks: string | null;
}

export interface OpDirectBillingRequest {
  patientId: number;
  consultantId: number | null;
  items: OpDirectBillingItemRequest[];
  paymentMode: PaymentMode;
  remarks: string | null;
}

export interface OpDirectBillingReceiptItem {
  id: number;
  categoryName: string;
  componentName: string;
  quantity: number;
  amount: number;
  remarks: string | null;
}

export interface OpDirectBillingReceipt {
  id: number;
  invoiceNumber: number;
  patientRegistrationNumber: string | null;
  patientName: string | null;
  gender: string | null;
  age: number | null;
  mobileNumber: string | null;
  consultantId: number | null;
  consultantName: string | null;
  items: OpDirectBillingReceiptItem[];
  totalAmount: number;
  paymentMode: PaymentMode;
  remarks: string | null;
  billedBy: string | null;
  billedAt: string;
  refundAmount: number | null;
}

/** Row shown in the Appointments screen's "Direct Billing" tab. */
export interface OpDirectBillingListEntry {
  id: number;
  invoiceNumber: number;
  patientName: string | null;
  patientRegistrationNumber: string | null;
  totalAmount: number;
  paymentMode: PaymentMode;
  billedBy: string | null;
  billedAt: string;
  refundAmount: number | null;
  consultantId: number | null;
  consultantName: string | null;
}

export interface OpDirectBillingListFilter {
  fromDate?: string;
  toDate?: string;
}
