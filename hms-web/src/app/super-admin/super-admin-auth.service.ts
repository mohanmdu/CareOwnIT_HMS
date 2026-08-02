import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

const STORAGE_KEY = 'hms_super_admin_auth';

interface DecodedSuperAdminToken {
  sub: string;
  superAdmin: true;
  exp: number;
}

/** No signature check here - purely for UI state, the server always re-validates on every request. */
function decodeToken(token: string): DecodedSuperAdminToken | null {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(normalized));
  } catch {
    return null;
  }
}

function readStoredToken(): DecodedSuperAdminToken | null {
  const token = sessionStorage.getItem(STORAGE_KEY);
  return token ? decodeToken(token) : null;
}

/**
 * Wholly separate session from AuthService (see hms-api's
 * SuperAdminLoginService / JwtService.issueSuperAdmin) - its own
 * sessionStorage key, its own token shape, its own login endpoint. Never
 * shares state with a tenant session, by design - see the multi-tenant
 * licensing plan §A.5/A.6 and auth.interceptor.ts's super-admin branch.
 */
@Injectable({ providedIn: 'root' })
export class SuperAdminAuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/super-admin/auth`;

  private readonly decoded = signal<DecodedSuperAdminToken | null>(readStoredToken());

  readonly currentUsername = computed(() => this.decoded()?.sub ?? null);

  isAuthenticated(): boolean {
    const decoded = this.decoded();
    return !!decoded && decoded.exp * 1000 > Date.now();
  }

  login(username: string, password: string): Observable<void> {
    return this.http.post<{ token: string }>(`${this.baseUrl}/login`, { username, password }).pipe(
      tap((response) => {
        sessionStorage.setItem(STORAGE_KEY, response.token);
        this.decoded.set(decodeToken(response.token));
      }),
      map(() => undefined)
    );
  }

  logout(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.decoded.set(null);
  }

  getToken(): string | null {
    return sessionStorage.getItem(STORAGE_KEY);
  }
}
