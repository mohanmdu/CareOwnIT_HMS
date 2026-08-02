import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Only the shared multi-tenant VPS build sets this - every offline/on-prem build stays on the unchanged 2-field form. See environment.deploymentMode and the multi-tenant licensing plan §A.8. */
  readonly showClientCode = environment.deploymentMode === 'multi-tenant';

  clientCode = '';
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
    this.auth.login(this.username, this.password, this.showClientCode ? this.clientCode : undefined).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl(this.auth.mustChangePassword() ? '/change-password' : (this.auth.defaultRoute() ?? '/dashboard'));
      },
      error: () => {
        this.submitting.set(false);
        this.errorMessage.set(this.showClientCode ? 'Incorrect client code, username, or password.' : 'Incorrect username or password.');
      }
    });
  }
}
