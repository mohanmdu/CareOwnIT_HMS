export type CollectionType =
  | 'APPOINTMENT_FEE'
  | 'OP_DIRECT_BILLING'
  | 'LAB_CHARGES'
  | 'INVESTIGATION_CHARGES'
  | 'IP_ADVANCE'
  | 'IP_FINAL_SETTLEMENT'
  | 'IP_DUE_COLLECTION'
  | 'PHARMACY_BILL';

export type BillType = 'OP' | 'IP';

export const COLLECTION_TYPE_LABELS: Record<string, string> = {
  APPOINTMENT_FEE: 'Appointment Fee',
  OP_DIRECT_BILLING: 'OP Direct Billing',
  LAB_CHARGES: 'Lab Charges',
  INVESTIGATION_CHARGES: 'Investigation Charges',
  IP_ADVANCE: 'IP Advance',
  IP_FINAL_SETTLEMENT: 'IP Final Settlement',
  IP_DUE_COLLECTION: 'IP Due Collection',
  PHARMACY_BILL: 'Pharmacy Bill'
};

/**
 * Every distinct Payment Mode literal that can appear across the 5 sources -
 * shown/filtered as-is, not canonicalized (PaymentMode/LabPaymentMode/
 * PharmacyPaymentMode's UPPERCASE enum names, AdmissionPaymentType's
 * CASH/INSURANCE/CORPORATE billing category, and the cashier-approval
 * flow's free-text Title Case strings all land in the same field).
 */
export const PAYMENT_MODE_OPTIONS: string[] = [
  'CASH',
  'CARD',
  'UPI',
  'NET_BANKING',
  'INSURANCE',
  'OTHER',
  'CHEQUE_DD',
  'ONLINE_FUND_TRANSFER',
  'CREDIT_PATIENT',
  'DEBIT_CARD',
  'CREDIT_CARD',
  'CORPORATE',
  'Cash',
  'Debit Card',
  'Credit Card',
  'Demand Draft',
  'Cheque',
  'Google Pay',
  'Phone Pe',
  'Paytm'
];

export interface CollectionReportRow {
  collectionType: CollectionType;
  billType: BillType;
  sourceId: number;
  patientName: string;
  patientUhid: string;
  billedAt: string | null;
  billNumber: string | null;
  paymentMode: string | null;
  consultantName: string | null;
  departmentName: string | null;
  billedByUsername: string | null;
  billedByDisplayName: string | null;
  departmentId: number | null;
  consultantId: number | null;
  invoiceAmount: number;
  discountAmount: number;
  doctorReferralAmount: number;
  receiptAmount: number;
  refundAmount: number;
}

export interface CollectionReportTotals {
  invoiceAmount: number;
  discountAmount: number;
  doctorReferralAmount: number;
  receiptAmount: number;
  refundAmount: number;
  totalCollectionAmount: number;
}

export interface CollectionReportUserSummary {
  username: string | null;
  displayName: string | null;
  transactionCount: number;
  invoiceAmount: number;
  discountAmount: number;
  receiptAmount: number;
  refundAmount: number;
  netCollectionAmount: number;
}

export interface CollectionReport {
  rows: CollectionReportRow[];
  totals: CollectionReportTotals;
  userSummary: CollectionReportUserSummary[];
}

export interface CollectionReportFilters {
  from: string;
  to: string;
  billType?: BillType;
  collectionType?: CollectionType;
  paymentMode?: string;
  departmentId?: number;
  consultantId?: number;
  username?: string;
}
