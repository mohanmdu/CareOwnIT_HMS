import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Role } from './role.model';

export type RoleInput = Pick<Role, 'name' | 'permittedModules' | 'permittedRoutes' | 'defaultRoute'>;

@Injectable({ providedIn: 'root' })
export class RoleService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/masters/roles`;

  list(): Observable<Role[]> {
    return this.http.get<Role[]>(this.baseUrl);
  }

  create(role: RoleInput): Observable<Role> {
    return this.http.post<Role>(this.baseUrl, role);
  }

  update(id: number, role: RoleInput): Observable<Role> {
    return this.http.put<Role>(`${this.baseUrl}/${id}`, role);
  }

  deactivate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }
}
