// 'WEBSITE' is deliberately excluded from PATIENT_ORIGIN_MODULE_OPTIONS below - it's only ever
// set programmatically by the public booking flow, never a choice staff make when registering someone in person.
export type PatientOriginModule = 'FRONT_OFFICE' | 'OP_APPOINTMENT' | 'IP_ADMISSION' | 'PHARMACY' | 'LAB' | 'INSURANCE' | 'WEBSITE';

export const PATIENT_ORIGIN_MODULE_OPTIONS: { value: PatientOriginModule; label: string }[] = [
  { value: 'FRONT_OFFICE', label: 'Front Office / Reception' },
  { value: 'OP_APPOINTMENT', label: 'OP Appointment' },
  { value: 'IP_ADMISSION', label: 'IP Admission' },
  { value: 'PHARMACY', label: 'Pharmacy' },
  { value: 'LAB', label: 'Lab / Investigations' },
  { value: 'INSURANCE', label: 'Insurance' }
];

export interface Patient {
  id: number | null;
  registrationNumber: string | null;
  firstName: string;
  lastName?: string | null;
  dateOfBirth?: string | null;
  gender?: string | null;
  age?: number | null;
  mobileNumber: string;
  email?: string | null;
  address?: string | null;
  active?: boolean;
  /** Set once at creation; never changed by later edits - see PatientOriginModule. */
  originModule?: PatientOriginModule | null;
  /** "/uploads/..." relative path, read-only - set via PatientService.uploadPhoto(), null until then. */
  photoPath?: string | null;
}

export interface PatientAuditLogEntry {
  id: number;
  operation: string;
  patientName: string;
  performedBy: string;
  performedAt: string;
}
