export interface OpCaseSheetHeader {
  appointmentId: number | null;
  directBillingId: number | null;
  source: PrescriptionWorklistSource;
  patientRegistrationNumber: string | null;
  patientName: string | null;
  gender: string | null;
  age: number | null;
  mobileNumber: string | null;
  appointmentDate: string;
  slotTime: string;
  consultantName: string | null;
}

export interface OpPrescriptionItem {
  id: number | null;
  drugName: string;
  qty: number | null;
  intake: string | null;
  morningDose: number | null;
  afternoonDose: number | null;
  eveningDose: number | null;
  nightDose: number | null;
}

export interface OpCaseSheet {
  id: number | null;
  header: OpCaseSheetHeader;
  foodDrugAllergy: string | null;
  heightCm: number | null;
  weightKg: number | null;
  bmi: number | null;
  temperatureF: number | null;
  pulseBpm: number | null;
  respirationBpm: number | null;
  bpSystolic: number | null;
  bpDiastolic: number | null;
  spo2: number | null;
  bodyFatPercent: number | null;
  chiefComplaints: string | null;
  pastMedicalCondition: string | null;
  currentMedication: string | null;
  physicalActivity: string | null;
  sleepDurationHours: string | null;
  smoking: string | null;
  alcohol: string | null;
  surgery: string | null;
  familyHistory: string | null;
  provisionalDiagnosis: string | null;
  cbg: string | null;
  findings: string | null;
  investigation: string | null;
  doctorNotes1: string | null;
  doctorNotes2: string | null;
  prescriptionItems: OpPrescriptionItem[];
  diet: string | null;
  remarks: string | null;
  reviewDate: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export type OpCaseSheetSaveRequest = Omit<OpCaseSheet, 'id' | 'header' | 'createdAt' | 'updatedAt'>;

export type PrescriptionWorklistSource = 'APPOINTMENT' | 'DIRECT_BILLING';

export interface PrescriptionWorklistEntry {
  appointmentId: number | null;
  directBillingId: number | null;
  source: PrescriptionWorklistSource;
  patientId: number;
  patientName: string | null;
  patientRegistrationNumber: string | null;
  patientAge: number | null;
  patientGender: string | null;
  mobileNumber: string | null;
  departmentName: string | null;
  consultantName: string | null;
  appointmentDate: string;
  slotTime: string;
  hasCaseSheet: boolean;
  reviewDate: string | null;
}

export interface ReviewDateReportEntry {
  appointmentId: number;
  patientName: string | null;
  patientRegistrationNumber: string | null;
  age: number | null;
  gender: string | null;
  mobileNumber: string | null;
  appointmentDate: string;
  consultantName: string | null;
  reviewDate: string;
}

export interface PrescriptionWorklistFilter {
  fromDate?: string;
  toDate?: string;
  consultantId?: number;
  search?: string;
}

export interface ReviewDateReportFilter {
  fromDate?: string;
  toDate?: string;
  upcoming: boolean;
}
