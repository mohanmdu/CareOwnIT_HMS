import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, ElementRef, HostListener, inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
import { ClinicLogoComponent } from '../../shared/ui/clinic-logo/clinic-logo.component';
import { ClinicSettingsService } from '../../features/masters-admin/clinic-settings/clinic-settings.service';
import { Tone } from '../../shared/ui/tone';
import { NAV_GROUPS, type NavGroup, type NavItem } from '../nav-config';
import { getVisibleNavGroups } from '../package-config';

/** True when `url` is exactly `route` or a child path under it (`/ip/admissions` matches `/ip/admissions/new`). */
function routeMatches(url: string, route: string): boolean {
  return url === route || url.startsWith(`${route}/`);
}

/** Cycled by each top-level group's fixed position in NAV_GROUPS (not the filtered/visible list), so a module's icon color stays the same regardless of which other modules a role/package can see. */
const ICON_TONE_CYCLE: Tone[] = ['primary', 'secondary', 'success', 'warning', 'danger', 'tertiary', 'info'];

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
    NgTemplateOutlet,
    FormsModule,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
    ClinicLogoComponent
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss'
})
export class AppShellComponent {
  private readonly auth = inject(AuthService);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly router = inject(Router);
  private readonly clinicSettingsService = inject(ClinicSettingsService);

  @ViewChild('searchInput') private readonly searchInputRef?: ElementRef<HTMLInputElement>;

  /** Recomputes if the signed-in user's role permissions ever change without a full page reload (e.g. after an admin edits their role and they re-authenticate). */
  readonly navGroups = computed(() => getVisibleNavGroups(this.auth.permittedModules(), this.auth.permittedRoutes()));

  /** Sidenav category split - "MAIN MODULES" vs "SYSTEM" - computed after package/role filtering so an unlicensed/unpermitted category never shows an empty heading. */
  readonly mainGroups = computed(() => this.navGroups().filter((group) => (group.category ?? 'main') === 'main'));
  readonly systemGroups = computed(() => this.navGroups().filter((group) => group.category === 'system'));

  // One shared fetch backs both the clinic name (sidenav brand) and the
  // optional custom footer text - ThemeService already applies header/footer
  // colors at bootstrap regardless, so an unconfigured deployment just shows
  // "HMS" as the name and no footer (today's look).
  private readonly clinicSettings = toSignal(
    this.clinicSettingsService.get().pipe(catchError(() => of(null))),
    { initialValue: null }
  );
  readonly clinicName = computed(() => this.clinicSettings()?.name ?? 'HMS');
  readonly footerText = computed(() => this.clinicSettings()?.footerText ?? null);

  readonly currentUsername = this.auth.currentUsername;
  readonly roleName = this.auth.roleName;
  readonly userInitials = computed(() => {
    const name = this.currentUsername();
    return name ? name.slice(0, 2).toUpperCase() : '?';
  });

  readonly isHandset = toSignal(
    this.breakpointObserver.observe(Breakpoints.Handset).pipe(map((result) => result.matches)),
    { initialValue: this.breakpointObserver.isMatched(Breakpoints.Handset) }
  );

  readonly sidenavOpened = signal(true);

  /** Single-open accordion: expanding a group collapses whichever one was open before. */
  readonly expandedGroup = signal<string | null>(null);

  readonly isFullscreen = signal(!!document.fullscreenElement);

  readonly searchQuery = signal('');
  /** Menu-only search for now (see the redesign plan) - filters the flattened NavItem list by label, not patients/reports. */
  readonly searchResults = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    if (!query) {
      return [];
    }
    const allItems: NavItem[] = this.navGroups().flatMap((group) => group.items);
    return allItems.filter((item) => item.label.toLowerCase().includes(query)).slice(0, 8);
  });

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

    document.addEventListener('fullscreenchange', () => this.isFullscreen.set(!!document.fullscreenElement));
  }

  @HostListener('window:keydown', ['$event'])
  onWindowKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.searchInputRef?.nativeElement.focus();
    }
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

  /** Top-level module icon color, matching the dashboard mockup's colorful icon-per-module look. */
  iconToneFor(group: NavGroup): Tone {
    const index = NAV_GROUPS.findIndex((g) => g.label === group.label);
    return ICON_TONE_CYCLE[index % ICON_TONE_CYCLE.length];
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

  toggleFullscreen(): void {
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      document.documentElement.requestFullscreen();
    }
  }

  navigateToFirstResult(): void {
    const results = this.searchResults();
    if (results.length > 0) {
      this.router.navigateByUrl(results[0].route);
      this.clearSearch();
    }
  }

  clearSearch(): void {
    this.searchQuery.set('');
  }

  signOut(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
