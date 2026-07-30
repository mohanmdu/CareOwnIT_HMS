import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DoctorQueueNowServing } from './doctor-queue.model';

/**
 * Separate from DoctorQueueService: targets the unauthenticated
 * /api/public/doctor-queue endpoint (no JWT sent, no login required) backing
 * the display board kiosk screen, as opposed to the staff-only
 * /api/registration/doctor-queue endpoints.
 */
@Injectable({ providedIn: 'root' })
export class DoctorQueueBoardService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/public/doctor-queue`;

  getNowServing(consultantId: number): Observable<DoctorQueueNowServing> {
    return this.http.get<DoctorQueueNowServing>(`${this.baseUrl}/now-serving`, {
      params: { consultantId: String(consultantId) }
    });
  }
}
