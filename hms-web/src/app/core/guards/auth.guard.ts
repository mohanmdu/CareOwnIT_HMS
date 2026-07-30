import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../../shared/services/notification.service';
import { ClinicSettingsService } from '../../features/masters-admin/clinic-settings/clinic-settings.service';
import { DOCTOR_QUEUE_ROUTES, NAV_GROUPS, routesForModule } from '../../layout/nav-config';
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
 *
 * Returns the matched NAV_GROUPS entry's OWN route (not the raw url, which
 * may be a nested/parameterized sub-path) so callers can check page-level
 * permission against exactly what the Role picker stores.
 */
function requiredModuleFor(url: string): { route: string; moduleKey: ModuleKey } | null {
  return ROUTE_TO_MODULE.find((entry) => url === entry.route || url.startsWith(`${entry.route}/`)) ?? null;
}

/** Mirrors getVisibleNavGroups()'s "never page-restricted" default - see package-config.ts. */
function isRoutePermitted(matchedRoute: string, moduleKey: ModuleKey, permittedRoutes: Set<string>): boolean {
  const modulePages = routesForModule(moduleKey);
  const pageLevelRestricted = modulePages.some((route) => permittedRoutes.has(route));
  return !pageLevelRestricted || permittedRoutes.has(matchedRoute);
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

  const match = requiredModuleFor(url);
  if (match) {
    if (!auth.permittedModules().has(match.moduleKey)) {
      return router.parseUrl('/dashboard');
    }
    if (!isRoutePermitted(match.route, match.moduleKey, auth.permittedRoutes())) {
      return router.parseUrl('/dashboard');
    }
  }

  // Doctor Queue Management is a runtime, admin-editable add-on (Clinic
  // Settings), not a role permission - checked separately, and only for its
  // own 3 routes, so every other navigation stays free of the extra request.
  if (DOCTOR_QUEUE_ROUTES.some((route) => url === route || url.startsWith(`${route}/`))) {
    const clinicSettingsService = inject(ClinicSettingsService);
    const notification = inject(NotificationService);
    return clinicSettingsService.get().pipe(
      map((settings) => {
        if (settings.doctorQueueEnabled) {
          return true;
        }
        notification.info('Doctor Queue Management is not enabled for this clinic.');
        return router.parseUrl('/dashboard');
      }),
      catchError(() => of(true))
    );
  }

  return true;
};
