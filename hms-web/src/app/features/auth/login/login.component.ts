import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';
import { PublicBranding, PublicBrandingService } from '../../../core/services/public-branding.service';
import { ThemeService } from '../../../core/services/theme.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatIconModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly publicBranding = inject(PublicBrandingService);
  private readonly themeService = inject(ThemeService);

  /** Only the shared multi-tenant VPS build sets this - every offline/on-prem build stays on the unchanged 2-field form. See environment.deploymentMode and the multi-tenant licensing plan §A.8. */
  readonly showClientCode = environment.deploymentMode === 'multi-tenant';

  clientCode = '';
  username = '';
  password = '';
  hidePassword = signal(true);
  submitting = signal(false);
  errorMessage = signal<string | null>(null);

  /**
   * Pre-authentication branding for whichever client code is currently
   * typed - null until resolved (or on a single-tenant build, which never
   * looks this up at all; there's only ever one tenant, and it's already
   * fully branded on the very next screen). See onClientCodeBlur().
   */
  branding = signal<PublicBranding | null>(null);

  readonly loginBackgroundImage = computed(() => {
    const url = this.branding()?.loginBackgroundUrl;
    return url ? `url('${url}')` : null;
  });

  togglePasswordVisibility(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  /** Fires once the admin/reception user tabs or clicks away from the Client Code field - a deliberately simple trigger (no debounce/live-as-you-type infra) for a field that's typically filled in one go, not edited character by character while watched. */
  onClientCodeBlur(): void {
    if (!this.showClientCode || !this.clientCode.trim()) {
      return;
    }
    this.publicBranding.getByClientCode(this.clientCode).subscribe((branding) => {
      this.branding.set(branding);
      this.themeService.applyTheme({
        themePrimaryColor: branding.themePrimaryColor,
        themeTertiaryColor: branding.themeTertiaryColor
      });
    });
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
