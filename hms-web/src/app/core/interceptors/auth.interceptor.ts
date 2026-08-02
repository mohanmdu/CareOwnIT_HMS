import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SuperAdminAuthService } from '../../super-admin/super-admin-auth.service';
import { AuthService } from '../services/auth.service';

const SUPER_ADMIN_API_PREFIX = '/api/super-admin/';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  // Super Admin requests carry their own token and their own 401 handling -
  // wholly separate from the tenant session below, by design. See the
  // multi-tenant licensing plan §A.5/A.6 and SuperAdminAuthService.
  if (req.url.includes(SUPER_ADMIN_API_PREFIX)) {
    const superAdminAuth = inject(SuperAdminAuthService);
    const superAdminToken = superAdminAuth.getToken();
    const authorized = superAdminToken
      ? req.clone({ setHeaders: { Authorization: `Bearer ${superAdminToken}` } })
      : req;
    return next(authorized).pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401 && !req.url.includes('/super-admin/auth/login')) {
          superAdminAuth.logout();
          router.navigateByUrl('/super-admin/login');
        }
        return throwError(() => error);
      })
    );
  }

  const auth = inject(AuthService);
  const token = auth.getToken();
  const authorized = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorized).pipe(
    catchError((error: unknown) => {
      // /settings/clinic is fetched unconditionally by ThemeService's
      // APP_INITIALIZER on every single bootstrap - including unauthenticated
      // routes (the login page, and the Doctor Queue display board kiosk) -
      // and is documented as "never blocks bootstrap on failure." A 401 here
      // must not force-redirect; that would hijack navigation on a route
      // that never required login in the first place.
      const isBootstrapThemeFetch = req.url.includes('/settings/clinic') && req.method === 'GET';
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !req.url.includes('/auth/login') &&
        !isBootstrapThemeFetch
      ) {
        auth.logout();
        router.navigateByUrl('/login');
      }
      return throwError(() => error);
    })
  );
};
