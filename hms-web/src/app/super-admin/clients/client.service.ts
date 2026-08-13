import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { ModuleKey } from '../../layout/package-config';
import {
  ClientAdminBootstrapInput,
  ClientAdminBootstrapResult,
  ClientCreateInput,
  ClientDatabaseRecord,
  ClientDomainStatus,
  ClientRecord
} from './client.model';

/** Reachable only with a Super Admin session - see auth.interceptor.ts's SUPER_ADMIN_API_PREFIX branch, which attaches SuperAdminAuthService's token instead of the tenant AuthService's. */
@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/super-admin/clients`;

  list(): Observable<ClientRecord[]> {
    return this.http.get<ClientRecord[]>(this.baseUrl);
  }

  create(input: ClientCreateInput): Observable<ClientRecord> {
    return this.http.post<ClientRecord>(this.baseUrl, input);
  }

  updateStatus(id: number, status: 'ACTIVE' | 'SUSPENDED'): Observable<ClientRecord> {
    return this.http.patch<ClientRecord>(`${this.baseUrl}/${id}/status`, { status });
  }

  updateModules(id: number, modules: Partial<Record<ModuleKey, boolean>>): Observable<ClientRecord> {
    return this.http.put<ClientRecord>(`${this.baseUrl}/${id}/modules`, modules);
  }

  /** Pass an empty string to clear the domain (stops this client's public site from being domain-routed). */
  updateDomain(id: number, domain: string): Observable<ClientRecord> {
    return this.http.patch<ClientRecord>(`${this.baseUrl}/${id}/domain`, { domain });
  }

  /** On-demand HTTPS reachability check - NOT_SET/UNREACHABLE are both normal, expected results (DNS/nginx/TLS setup is a separate manual step), not errors. */
  checkDomainStatus(id: number): Observable<{ status: ClientDomainStatus }> {
    return this.http.get<{ status: ClientDomainStatus }>(`${this.baseUrl}/${id}/domain/status`);
  }

  /** One-time bootstrap for a brand-new client's very first user - see hms-api's ClientAdminBootstrapService. Every user after this one is created the normal way, by this admin, through the tenant's own General Users screen. */
  bootstrapAdmin(id: number, input: ClientAdminBootstrapInput): Observable<ClientAdminBootstrapResult> {
    return this.http.post<ClientAdminBootstrapResult>(`${this.baseUrl}/${id}/admin`, input);
  }

  getDatabase(id: number): Observable<ClientDatabaseRecord> {
    return this.http.get<ClientDatabaseRecord>(`${this.baseUrl}/${id}/database`);
  }

  /** Creates the DB/user, runs the full tenant migration set, flips status to READY - slow (a real 90+ migration run) by design, not an instant call. Safe to call again on a client stuck in FAILED. */
  provisionDatabase(id: number): Observable<ClientDatabaseRecord> {
    return this.http.post<ClientDatabaseRecord>(`${this.baseUrl}/${id}/database`, {});
  }
}
