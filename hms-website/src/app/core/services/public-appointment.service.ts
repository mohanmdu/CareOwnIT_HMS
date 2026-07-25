import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PatientLookupResult, PublicBookingRequest, PublicBookingResult, PublicSlot } from '../models/public.model';

@Injectable({ providedIn: 'root' })
export class PublicAppointmentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/public/appointments`;

  slots(consultantId: number, date: string): Observable<PublicSlot[]> {
    const params = new HttpParams().set('consultantId', consultantId).set('date', date);
    return this.http.get<PublicSlot[]>(`${this.baseUrl}/slots`, { params });
  }

  lookupByMobile(mobileNumber: string): Observable<PatientLookupResult[]> {
    return this.http.post<PatientLookupResult[]>(`${this.baseUrl}/patient-lookup`, { mobileNumber });
  }

  book(request: PublicBookingRequest): Observable<PublicBookingResult> {
    return this.http.post<PublicBookingResult>(`${this.baseUrl}/book`, request);
  }
}
