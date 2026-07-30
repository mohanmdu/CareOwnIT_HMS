import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Consultant } from './consultant.model';
import { ConsultantAvailability } from './consultant-timing.model';

export interface ConsultantInput {
  name: string;
  departmentId: number;
  specializationId: number | null;
  email: string | null;
  mobileNumber: string | null;
  consultationFee: number;
  profile: string | null;
  address: string | null;
  acceptingAppointments: boolean;
  queueTokenPrefix: string | null;
  consultingRoomLabel: string | null;
}

@Injectable({ providedIn: 'root' })
export class ConsultantService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/masters/consultants`;

  /** Active consultants only. */
  list(): Observable<Consultant[]> {
    return this.http.get<Consultant[]>(this.baseUrl);
  }

  listInactive(): Observable<Consultant[]> {
    return this.http.get<Consultant[]>(`${this.baseUrl}/inactive`);
  }

  search(query: string): Observable<Consultant[]> {
    return this.http.get<Consultant[]>(`${this.baseUrl}/search`, { params: new HttpParams().set('q', query) });
  }

  create(consultant: ConsultantInput): Observable<Consultant> {
    return this.http.post<Consultant>(this.baseUrl, consultant);
  }

  update(id: number, consultant: ConsultantInput): Observable<Consultant> {
    return this.http.put<Consultant>(`${this.baseUrl}/${id}`, consultant);
  }

  deactivate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  restore(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/restore`, {});
  }

  publish(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/publish`, {});
  }

  unpublish(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/unpublish`, {});
  }

  uploadImage(id: number, file: File): Observable<Consultant> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Consultant>(`${this.baseUrl}/${id}/image`, formData);
  }

  getAvailability(id: number): Observable<ConsultantAvailability> {
    return this.http.get<ConsultantAvailability>(`${this.baseUrl}/${id}/timings`);
  }

  saveAvailability(id: number, availability: ConsultantAvailability): Observable<ConsultantAvailability> {
    return this.http.put<ConsultantAvailability>(`${this.baseUrl}/${id}/timings`, availability);
  }
}
