import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PatientReport, PatientReportAuditLogRow } from './patient-report.model';

@Injectable({ providedIn: 'root' })
export class PatientReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/registration/patient-reports`;

  upload(patientId: number, comments: string | null, file: File): Observable<PatientReport> {
    const formData = new FormData();
    formData.append('patientId', String(patientId));
    if (comments) {
      formData.append('comments', comments);
    }
    formData.append('file', file);
    return this.http.post<PatientReport>(this.baseUrl, formData);
  }

  get(id: number): Observable<PatientReport> {
    return this.http.get<PatientReport>(`${this.baseUrl}/${id}`);
  }

  /** A time-limited, unauthenticated URL for this one report - see JwtService.issueReportShareToken() on the backend. */
  createShareLink(id: number): Observable<{ url: string }> {
    return this.http.post<{ url: string }>(`${this.baseUrl}/${id}/share-link`, {});
  }

  getActiveFiles(patientId: number): Observable<PatientReport[]> {
    return this.http.get<PatientReport[]>(`${this.baseUrl}/active`, { params: { patientId } });
  }

  getDeletedFiles(patientId: number): Observable<PatientReport[]> {
    return this.http.get<PatientReport[]>(`${this.baseUrl}/deleted`, { params: { patientId } });
  }

  softDelete(id: number, reason: string): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/delete`, { reason });
  }

  permanentDelete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  getPatientAuditLog(): Observable<PatientReportAuditLogRow[]> {
    return this.http.get<PatientReportAuditLogRow[]>(`${this.baseUrl}/audit-log/patient`);
  }

  getDoctorAuditLog(): Observable<PatientReportAuditLogRow[]> {
    return this.http.get<PatientReportAuditLogRow[]>(`${this.baseUrl}/audit-log/doctor`);
  }
}
