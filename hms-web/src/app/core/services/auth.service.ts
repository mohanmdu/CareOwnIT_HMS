import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import type { ModuleKey } from '../../layout/package-config';
import { setLicensedModuleKeys } from '../../layout/package-config';

const STORAGE_KEY = 'hms_auth';

interface DecodedToken {
  sub: string;
  roleName: string;
  modules: ModuleKey[];
  routes: string[];
  defaultRoute: string | null;
  mustChangePassword: boolean;
  /** Present in both deployment modes (see hms-api's JwtService.issue) - which Client this user belongs to. Not otherwise used by the frontend today; kept for parity with the claim itself. */
  clientId: number;
  /** UI convenience only - see package-config.ts's activeModuleKeys(). The backend re-checks the license fresh on every request regardless of what this claim says. */
  licensedModules: ModuleKey[];
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

  private readonly decoded = signal<DecodedToken | null>(this.setDecoded(readStoredToken()));

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

  /** clientCode is ignored by the backend in single-tenant mode (see environment.deploymentMode / login.component) - always safe to pass through. */
  login(username: string, password: string, clientCode?: string): Observable<void> {
    return this.http.post<{ token: string }>(`${this.baseUrl}/login`, { clientCode, username, password }).pipe(
      tap((response) => {
        sessionStorage.setItem(STORAGE_KEY, response.token);
        this.decoded.set(this.setDecoded(decodeToken(response.token)));
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
        this.decoded.set(this.setDecoded(decodeToken(response.token)));
      }),
      map(() => undefined)
    );
  }

  logout(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.decoded.set(this.setDecoded(null));
  }

  getToken(): string | null {
    return sessionStorage.getItem(STORAGE_KEY);
  }

  /** Keeps package-config.ts's activeModuleKeys() in sync with whatever token this service just decoded (including null, on logout) - see setLicensedModuleKeys()'s own doc comment for why this is a plain module-level setter rather than DI. Returns its input so it can be used inline in the decoded signal's initializer. */
  private setDecoded(decoded: DecodedToken | null): DecodedToken | null {
    setLicensedModuleKeys(decoded?.licensedModules ?? null);
    return decoded;
  }
}
