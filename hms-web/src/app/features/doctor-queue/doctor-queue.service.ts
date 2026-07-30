import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DoctorQueueAuditLog,
  DoctorQueueDashboard,
  DoctorQueueEntry,
  DoctorQueueWorklistEntry,
  WalkInRequest
} from './doctor-queue.model';

@Injectable({ providedIn: 'root' })
export class DoctorQueueService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/registration/doctor-queue`;

  getWorklist(consultantId: number, date: string): Observable<DoctorQueueWorklistEntry[]> {
    return this.http.get<DoctorQueueWorklistEntry[]>(`${this.baseUrl}/worklist`, {
      params: { consultantId: String(consultantId), date }
    });
  }

  getDashboard(consultantId: number, date: string): Observable<DoctorQueueDashboard> {
    return this.http.get<DoctorQueueDashboard>(`${this.baseUrl}/dashboard`, {
      params: { consultantId: String(consultantId), date }
    });
  }

  get(id: number): Observable<DoctorQueueEntry> {
    return this.http.get<DoctorQueueEntry>(`${this.baseUrl}/${id}`);
  }

  getAuditLogs(): Observable<DoctorQueueAuditLog[]> {
    return this.http.get<DoctorQueueAuditLog[]>(`${this.baseUrl}/audit-logs`);
  }

  getEntryAuditLogs(id: number): Observable<DoctorQueueAuditLog[]> {
    return this.http.get<DoctorQueueAuditLog[]>(`${this.baseUrl}/${id}/audit-logs`);
  }

  checkIn(appointmentId: number): Observable<DoctorQueueEntry> {
    return this.http.post<DoctorQueueEntry>(`${this.baseUrl}/check-in`, { appointmentId });
  }

  registerWalkIn(input: WalkInRequest): Observable<DoctorQueueEntry> {
    return this.http.post<DoctorQueueEntry>(`${this.baseUrl}/walk-in`, input);
  }

  nextPatient(consultantId: number, date: string): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(
      `${this.baseUrl}/next-patient`,
      {},
      { params: { consultantId: String(consultantId), date } }
    );
  }

  recall(id: number): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(`${this.baseUrl}/${id}/recall`, {});
  }

  skip(id: number): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(`${this.baseUrl}/${id}/skip`, {});
  }

  complete(id: number): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(`${this.baseUrl}/${id}/complete`, {});
  }

  escalate(id: number, reason?: string): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(`${this.baseUrl}/${id}/escalate`, reason ? { reason } : {});
  }

  deEscalate(id: number): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(`${this.baseUrl}/${id}/de-escalate`, {});
  }

  cancel(id: number, reason: string): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(`${this.baseUrl}/${id}/cancel`, { reason });
  }

  moveToWaiting(id: number): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(`${this.baseUrl}/${id}/move-to-waiting`, {});
  }

  markNoShow(id: number, reason?: string): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(`${this.baseUrl}/${id}/no-show`, reason ? { reason } : {});
  }

  noShowForAppointment(appointmentId: number, reason?: string): Observable<DoctorQueueEntry> {
    return this.http.patch<DoctorQueueEntry>(
      `${this.baseUrl}/no-show-for-appointment`,
      reason ? { appointmentId, reason } : { appointmentId }
    );
  }
}
