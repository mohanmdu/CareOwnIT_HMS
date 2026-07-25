import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { GeneralUser, GeneralUserAuditLogEntry } from './general-user.model';

export type GeneralUserProfileInput = Pick<GeneralUser, 'name' | 'mobileNumber' | 'email' | 'roleId' | 'username'>;
export type GeneralUserCreateInput = GeneralUserProfileInput & { initialPassword: string };

@Injectable({ providedIn: 'root' })
export class GeneralUserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/masters/general-users`;

  /** Active users only. */
  list(): Observable<GeneralUser[]> {
    return this.http.get<GeneralUser[]>(this.baseUrl);
  }

  listInactive(): Observable<GeneralUser[]> {
    return this.http.get<GeneralUser[]>(`${this.baseUrl}/inactive`);
  }

  create(user: GeneralUserCreateInput): Observable<GeneralUser> {
    return this.http.post<GeneralUser>(this.baseUrl, user);
  }

  update(id: number, user: GeneralUserProfileInput): Observable<GeneralUser> {
    return this.http.put<GeneralUser>(`${this.baseUrl}/${id}`, user);
  }

  /** Admin-initiated reset (e.g. the user forgot their password) - forces the user to pick a new one at next login. */
  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/reset-password`, { newPassword });
  }

  deactivate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  restore(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/restore`, {});
  }

  auditLogs(): Observable<GeneralUserAuditLogEntry[]> {
    return this.http.get<GeneralUserAuditLogEntry[]>(`${this.baseUrl}/audit-logs`);
  }
}
