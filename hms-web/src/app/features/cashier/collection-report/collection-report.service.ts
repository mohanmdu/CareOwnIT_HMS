import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CollectionReport, CollectionReportFilters } from './collection-report.model';

@Injectable({ providedIn: 'root' })
export class CollectionReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/cashier/reports/collection`;

  search(filters: CollectionReportFilters): Observable<CollectionReport> {
    let params = new HttpParams().set('from', filters.from).set('to', filters.to);
    if (filters.billType) {
      params = params.set('billType', filters.billType);
    }
    if (filters.collectionType) {
      params = params.set('collectionType', filters.collectionType);
    }
    if (filters.paymentMode) {
      params = params.set('paymentMode', filters.paymentMode);
    }
    if (filters.departmentId) {
      params = params.set('departmentId', filters.departmentId);
    }
    if (filters.consultantId) {
      params = params.set('consultantId', filters.consultantId);
    }
    if (filters.username) {
      params = params.set('username', filters.username);
    }
    return this.http.get<CollectionReport>(this.baseUrl, { params });
  }
}
