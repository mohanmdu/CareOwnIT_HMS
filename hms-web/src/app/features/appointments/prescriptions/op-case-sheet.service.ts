import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  OpCaseSheet,
  OpCaseSheetSaveRequest,
  PrescriptionWorklistEntry,
  PrescriptionWorklistFilter,
  ReviewDateReportEntry,
  ReviewDateReportFilter
} from './op-case-sheet.model';

@Injectable({ providedIn: 'root' })
export class OpCaseSheetService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/registration/op-case-sheets`;

  worklist(filter?: PrescriptionWorklistFilter): Observable<PrescriptionWorklistEntry[]> {
    const params: Record<string, string> = {};
    if (filter?.fromDate) {
      params['fromDate'] = filter.fromDate;
    }
    if (filter?.toDate) {
      params['toDate'] = filter.toDate;
    }
    if (filter?.consultantId) {
      params['consultantId'] = String(filter.consultantId);
    }
    if (filter?.search) {
      params['search'] = filter.search;
    }
    return this.http.get<PrescriptionWorklistEntry[]>(`${this.baseUrl}/worklist`, { params });
  }

  getByAppointment(appointmentId: number): Observable<OpCaseSheet> {
    return this.http.get<OpCaseSheet>(`${this.baseUrl}/by-appointment/${appointmentId}`);
  }

  save(appointmentId: number, request: OpCaseSheetSaveRequest): Observable<OpCaseSheet> {
    return this.http.put<OpCaseSheet>(`${this.baseUrl}/by-appointment/${appointmentId}`, request);
  }

  getByDirectBilling(directBillingId: number): Observable<OpCaseSheet> {
    return this.http.get<OpCaseSheet>(`${this.baseUrl}/by-direct-billing/${directBillingId}`);
  }

  saveDirectBilling(directBillingId: number, request: OpCaseSheetSaveRequest): Observable<OpCaseSheet> {
    return this.http.put<OpCaseSheet>(`${this.baseUrl}/by-direct-billing/${directBillingId}`, request);
  }

  reviewDateReport(filter: ReviewDateReportFilter): Observable<ReviewDateReportEntry[]> {
    const params: Record<string, string> = { upcoming: String(filter.upcoming) };
    if (filter.fromDate) {
      params['fromDate'] = filter.fromDate;
    }
    if (filter.toDate) {
      params['toDate'] = filter.toDate;
    }
    return this.http.get<ReviewDateReportEntry[]>(`${this.baseUrl}/review-date-report`, { params });
  }
}
