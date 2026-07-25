import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import type { ModuleKey } from '../../layout/package-config';

const STORAGE_KEY = 'hms_auth';

interface DecodedToken {
  sub: string;
  roleName: string;
  modules: ModuleKey[];
  routes: string[];
  defaultRoute: string | null;
  mustChangePassword: boolean;
  exp: number;
}

/** No signature check here - purely for UI state, the server always re-validates on every request. */
function decodeToken(token: string): DecodedToken | null {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(normalized));
  } catch {
    return null;
  }
}

function readStoredToken(): DecodedToken | null {
  const token = sessionStorage.getItem(STORAGE_KEY);
  return token ? decodeToken(token) : null;
}

/** Real JWT-based auth (see hms-api com.pms.security). Decodes the stored token on construction so a page refresh doesn't lose the signed-in state. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  private readonly decoded = signal<DecodedToken | null>(readStoredToken());

  readonly currentUsername = computed(() => this.decoded()?.sub ?? null);
  readonly roleName = computed(() => this.decoded()?.roleName ?? null);
  readonly permittedModules = computed<Set<ModuleKey>>(() => new Set(this.decoded()?.modules ?? []));
  readonly permittedRoutes = computed<Set<string>>(() => new Set(this.decoded()?.routes ?? []));
  /** Where this role lands right after login - see Role.defaultRoute. Null means the default '/dashboard'. */
  readonly defaultRoute = computed(() => this.decoded()?.defaultRoute ?? null);
  readonly mustChangePassword = computed(() => this.decoded()?.mustChangePassword ?? false);

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

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<{ token: string }>(`${this.baseUrl}/change-password`, { currentPassword, newPassword }).pipe(
      tap((response) => {
        // The old token still has mustChangePassword=true baked in (JWTs are
        // immutable once issued) - store the freshly-issued one the backend
        // returns, or a page refresh would re-decode the stale claim from
        // sessionStorage and bounce the user back into this screen forever.
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
