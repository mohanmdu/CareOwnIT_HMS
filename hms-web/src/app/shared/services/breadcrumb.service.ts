import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NAV_GROUPS } from '../../layout/nav-config';

export interface BreadcrumbEntry {
  label: string;
  route: string | null;
}

/**
 * Computes a breadcrumb trail from the current URL with zero per-page
 * wiring - every page that uses <app-page-header> gets one for free. Finds
 * whichever NavGroup owns the current URL via longest-route-prefix match
 * across every NavItem (same matching philosophy as the backend's
 * ModulePathMappings.resolve()), so it also works for dynamic routes that
 * aren't themselves a literal nav-config entry (e.g. /ip/admissions/2/admit
 * still resolves under "IP Admission Module" via its /ip/admissions prefix).
 */
@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private readonly router = inject(Router);

  trail(): BreadcrumbEntry[] {
    const url = this.router.url.split('?')[0].split('#')[0];
    const home: BreadcrumbEntry = { label: 'Home', route: '/' };

    let bestGroupLabel: string | null = null;
    let bestGroupFirstRoute: string | null = null;
    let bestLength = -1;

    for (const group of NAV_GROUPS) {
      for (const item of group.items) {
        const matches = url === item.route || url.startsWith(`${item.route}/`);
        if (matches && item.route.length > bestLength) {
          bestLength = item.route.length;
          bestGroupLabel = group.label;
          bestGroupFirstRoute = group.items[0].route;
        }
      }
    }

    if (bestGroupLabel === null) {
      return [home];
    }
    return [home, { label: bestGroupLabel, route: bestGroupFirstRoute }];
  }
}
