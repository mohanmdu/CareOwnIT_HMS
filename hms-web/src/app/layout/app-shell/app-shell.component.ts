import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, map, of } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ClinicSettingsService } from '../../features/masters-admin/clinic-settings/clinic-settings.service';
import type { NavGroup } from '../nav-config';
import { getVisibleNavGroups } from '../package-config';

/** True when `url` is exactly `route` or a child path under it (`/ip/admissions` matches `/ip/admissions/new`). */
function routeMatches(url: string, route: string): boolean {
  return url === route || url.startsWith(`${route}/`);
}

/**
 * App-wide shell: responsive sidenav + top toolbar, replacing the flat
 * always-visible header/nav in the pre-redesign AppComponent. Rendered as
 * the layout component for every authenticated route (see app.routes.ts);
 * the login screen sits outside it.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss'
})
export class AppShellComponent {
  private readonly auth = inject(AuthService);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly router = inject(Router);
  private readonly clinicSettingsService = inject(ClinicSettingsService);

  /** Recomputes if the signed-in user's role permissions ever change without a full page reload (e.g. after an admin edits their role and they re-authenticate). */
  readonly navGroups = computed(() => getVisibleNavGroups(this.auth.permittedModules()));

  // Footer only renders when a client has actually set custom footer text -
  // ThemeService already applies header/footer colors at bootstrap regardless,
  // so an unconfigured deployment shows no footer at all (today's look).
  readonly footerText = toSignal(
    this.clinicSettingsService.get().pipe(
      map((settings) => settings.footerText),
      catchError(() => of(null))
    ),
    { initialValue: null }
  );

  readonly isHandset = toSignal(
    this.breakpointObserver.observe(Breakpoints.Handset).pipe(map((result) => result.matches)),
    { initialValue: this.breakpointObserver.isMatched(Breakpoints.Handset) }
  );

  readonly sidenavOpened = signal(true);

  /** Single-open accordion: expanding a group collapses whichever one was open before. */
  readonly expandedGroup = signal<string | null>(null);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url)
    ),
    { initialValue: this.router.url }
  );

  constructor() {
    effect(() => {
      this.sidenavOpened.set(!this.isHandset());
    }, { allowSignalWrites: true });

    // Auto-expand whichever section contains the active route, so a direct
    // link or a page refresh doesn't leave the user's current page buried
    // inside a collapsed section.
    effect(() => {
      const url = this.currentUrl();
      const activeGroup = this.navGroups().find(
        (group) => group.items.length > 1 && group.items.some((item) => routeMatches(url, item.route))
      );
      if (activeGroup) {
        this.expandedGroup.set(activeGroup.label);
      }
    }, { allowSignalWrites: true });
  }

  toggleSidenav(): void {
    this.sidenavOpened.update((opened) => !opened);
  }

  toggleGroup(label: string): void {
    this.expandedGroup.update((current) => (current === label ? null : label));
  }

  isGroupExpanded(group: NavGroup): boolean {
    return this.expandedGroup() === group.label;
  }

  /** Highlights a collapsed group's header when one of its own routes is active, so it stays orientating even before the user expands it. */
  isGroupActive(group: NavGroup): boolean {
    const url = this.currentUrl();
    return group.items.some((item) => routeMatches(url, item.route));
  }

  closeOnMobileNav(): void {
    if (this.isHandset()) {
      this.sidenavOpened.set(false);
    }
  }

  signOut(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
