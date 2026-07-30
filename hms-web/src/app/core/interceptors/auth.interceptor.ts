import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
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
