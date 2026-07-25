import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { NAV_GROUPS } from '../../layout/nav-config';
import type { ModuleKey } from '../../layout/package-config';

const CHANGE_PASSWORD_ROUTE = '/change-password';

/**
 * route -> required module, derived from NAV_GROUPS itself so it can't drift
 * from the sidenav. Sorted longest-route-first so prefix matching below picks
 * the most specific entry for nested/parameterized routes (e.g.
 * '/registration/patients/history' over '/registration/patients').
 */
const ROUTE_TO_MODULE: Array<{ route: string; moduleKey: ModuleKey }> = NAV_GROUPS.flatMap((group) =>
  group.items.map((item) => ({ route: item.route, moduleKey: item.moduleKey ?? group.moduleKey }))
).sort((a, b) => b.route.length - a.route.length);

/**
 * A route with no NAV_GROUPS entry (e.g. a detail/edit route nested under a
 * listed one) is allowed by default - this guard is a UX nicety that hides
 * unreachable sidenav links early, not the real security boundary. The
 * backend's ModuleAuthorizationManager is what actually enforces access.
 */
function requiredModuleFor(url: string): ModuleKey | null {
  const match = ROUTE_TO_MODULE.find((entry) => url === entry.route || url.startsWith(`${entry.route}/`));
  return match?.moduleKey ?? null;
}

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.parseUrl('/login');
  }

  const url = state.url.split('?')[0];

  if (auth.mustChangePassword() && url !== CHANGE_PASSWORD_ROUTE) {
    return router.parseUrl(CHANGE_PASSWORD_ROUTE);
  }

  const requiredModule = requiredModuleFor(url);
  if (requiredModule && !auth.permittedModules().has(requiredModule)) {
    return router.parseUrl('/dashboard');
  }

  return true;
};
