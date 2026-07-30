import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteConfigService } from '../../core/services/site-config.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Thumb-reachable Call/Book bar shown only below 960px (patients overwhelmingly
 * book from a phone - see the redesign discussion) - mounted once in
 * app.component.html, hidden entirely on desktop via CSS only.
 */
@Component({
  selector: 'app-mobile-cta-bar',
  standalone: true,
  imports: [RouterLink, IconComponent],
  template: `
    <div class="mobile-cta-spacer"></div>
    <div class="mobile-cta-bar">
      @if (config()?.phone) {
        <a [href]="'tel:' + config()?.phone" class="btn btn-outline btn-sm"><app-icon name="phone" /> Call</a>
      }
      <a routerLink="/book-appointment" class="btn btn-primary btn-sm btn-block">
        <app-icon name="calendar" /> Book Appointment
      </a>
    </div>
  `,
  styles: [
    `
      .mobile-cta-bar {
        position: fixed;
        left: 0;
        right: 0;
        bottom: 0;
        z-index: 90;
        display: flex;
        gap: var(--space-3);
        padding: var(--space-3) var(--space-4);
        padding-bottom: calc(var(--space-3) + env(safe-area-inset-bottom));
        background: color-mix(in srgb, var(--color-bg) 92%, transparent);
        backdrop-filter: blur(16px);
        -webkit-backdrop-filter: blur(16px);
        border-top: 1px solid var(--color-border);
        box-shadow: 0 -8px 24px -8px rgba(14, 31, 27, 0.18);
      }
      .mobile-cta-spacer {
        height: 78px;
      }
      @media (min-width: 960px) {
        .mobile-cta-bar,
        .mobile-cta-spacer {
          display: none;
        }
      }
    `
  ]
})
export class MobileCtaBarComponent {
  private readonly siteConfig = inject(SiteConfigService);
  readonly config = this.siteConfig.config;
}
