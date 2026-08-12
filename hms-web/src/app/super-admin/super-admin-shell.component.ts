import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SuperAdminAuthService } from './super-admin-auth.service';

/**
 * Deliberately not AppShellComponent (the tenant sidenav) - no NAV_GROUPS,
 * no module filtering, nothing tenant-shaped at all. A thin top bar plus a
 * router-outlet is all Super Admin's small screen set needs. See the
 * multi-tenant licensing plan §A.6.
 */
@Component({
  selector: 'app-super-admin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatIconModule],
  templateUrl: './super-admin-shell.component.html',
  styleUrl: './super-admin-shell.component.scss'
})
export class SuperAdminShellComponent {
  private readonly auth = inject(SuperAdminAuthService);
  private readonly router = inject(Router);

  readonly currentUsername = this.auth.currentUsername;

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/super-admin/login');
  }
}
