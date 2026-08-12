import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { SuperAdminAuthService } from '../super-admin-auth.service';

/** Deliberately outside AppShellComponent/the tenant sidenav - a Super Admin session is never reachable from, or visible to, a tenant login. See the multi-tenant licensing plan §A.6. */
@Component({
  selector: 'app-super-admin-login',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatIconModule],
  templateUrl: './super-admin-login.component.html',
  styleUrl: './super-admin-login.component.scss'
})
export class SuperAdminLoginComponent {
  private readonly auth = inject(SuperAdminAuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  hidePassword = signal(true);
  submitting = signal(false);
  errorMessage = signal<string | null>(null);

  togglePasswordVisibility(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  login(): void {
    if (this.submitting()) {
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl('/super-admin/clients');
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set('Incorrect username or password.');
      }
    });
  }
}
