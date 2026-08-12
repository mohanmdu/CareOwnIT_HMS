import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClientBackupAuditEntry, ClientBackupRecord } from './backup.model';

/** Reachable only with a Super Admin session - see ClientService's own doc comment on the auth interceptor that makes that true for anything under /super-admin/. */
@Injectable({ providedIn: 'root' })
export class BackupService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/super-admin/backups`;

  list(): Observable<ClientBackupRecord[]> {
    return this.http.get<ClientBackupRecord[]>(this.baseUrl);
  }

  auditLog(clientId: number): Observable<ClientBackupAuditEntry[]> {
    return this.http.get<ClientBackupAuditEntry[]>(`${this.baseUrl}/${clientId}/audit-log`);
  }

  /** Synchronous and potentially slow for a large database - same tradeoff as ClientService.provisionDatabase(). */
  runNow(clientId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${clientId}/run`, {});
  }

  /** Blob response type so the caller can trigger a real browser download (see backup-list.component.ts) - a plain <a href> can't carry the Bearer token this endpoint requires. */
  download(clientId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${clientId}/download`, { responseType: 'blob' });
  }

  /** confirmClientCode must exactly match the target client's own code - see hms-api's RestoreService for why this is enforced server-side too, not just as a UI gate. */
  restore(clientId: number, confirmClientCode: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${clientId}/restore`, { confirmClientCode });
  }
}
