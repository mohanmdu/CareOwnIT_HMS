import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { SiteConfigService } from '../../core/services/site-config.service';
import { IconComponent } from '../../shared/icon/icon.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, IconComponent],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent {
  private readonly siteConfig = inject(SiteConfigService);
  readonly config = this.siteConfig.config;

  readonly navOpen = signal(false);

  /** Main row - the 5 pages patients look for most often. */
  readonly primaryNavLinks = [
    { label: 'Departments', path: '/departments' },
    { label: 'Doctors', path: '/doctors' },
    { label: 'Health Packages', path: '/health-packages' },
    { label: 'About Us', path: '/about' },
    { label: 'Contact', path: '/contact' }
  ];

  /** Slim top utility bar on desktop - kept out of the main row so it stays uncluttered; folded into the mobile menu below 960px so nothing is ever unreachable. */
  readonly secondaryNavLinks = [
    { label: 'Gallery', path: '/gallery' },
    { label: 'News & Events', path: '/news-events' },
    { label: 'Testimonials', path: '/testimonials' },
    { label: 'Blog', path: '/blog' },
    { label: 'Careers', path: '/careers' },
    { label: 'FAQ', path: '/faq' }
  ];

  readonly allNavLinks = [...this.primaryNavLinks, ...this.secondaryNavLinks];

  toggleNav(): void {
    this.navOpen.update((open) => !open);
  }

  closeNav(): void {
    this.navOpen.set(false);
  }
}
