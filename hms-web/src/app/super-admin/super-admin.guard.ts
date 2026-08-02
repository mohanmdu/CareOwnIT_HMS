import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SuperAdminAuthService } from './super-admin-auth.service';

/** Structurally separate from auth.guard.ts (tenant) - checks SuperAdminAuthService, never AuthService, and vice versa. See the multi-tenant licensing plan §A.6. */
export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(SuperAdminAuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : router.parseUrl('/super-admin/login');
};
