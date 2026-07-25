import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { PatientSummary } from '../patient-history/patient-summary.model';
import { Patient, PatientAuditLogEntry } from './patient.model';

export type PatientInput = Omit<Patient, 'id' | 'registrationNumber'>;

@Injectable({ providedIn: 'root' })
export class PatientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/registration/patients`;

  /** Active patients only - used both by the registration grid and every workflow's patient picker. */
  search(query: string): Observable<Patient[]> {
    return this.http.get<Patient[]>(this.baseUrl, { params: query ? { query } : {} });
  }

  get(id: number): Observable<Patient> {
    return this.http.get<Patient>(`${this.baseUrl}/${id}`);
  }

  /** One-round-trip consolidated view (demographics + admissions + appointments + investigations + billing + payments + files) - backs the Patient Information (OP/IP) screen. */
  summary(id: number): Observable<PatientSummary> {
    return this.http.get<PatientSummary>(`${this.baseUrl}/${id}/summary`);
  }

  uploadPhoto(id: number, file: File): Observable<Patient> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Patient>(`${this.baseUrl}/${id}/photo`, formData);
  }

  listInactive(): Observable<Patient[]> {
    return this.http.get<Patient[]>(`${this.baseUrl}/inactive`);
  }

  register(patient: PatientInput): Observable<Patient> {
    return this.http.post<Patient>(this.baseUrl, patient);
  }

  update(id: number, patient: PatientInput): Observable<Patient> {
    return this.http.put<Patient>(`${this.baseUrl}/${id}`, patient);
  }

  /** Soft delete - moves the patient to the Inactive Patients list. */
  softDelete(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  restore(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/restore`, {});
  }

  /** Irreversible - actually removes the record, unlike softDelete(). */
  permanentDelete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  auditLogs(): Observable<PatientAuditLogEntry[]> {
    return this.http.get<PatientAuditLogEntry[]>(`${this.baseUrl}/audit-logs`);
  }
}
