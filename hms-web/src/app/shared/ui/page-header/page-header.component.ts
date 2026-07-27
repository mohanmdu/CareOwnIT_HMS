import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BreadcrumbService } from '../../services/breadcrumb.service';

/**
 * Consistent title/subtitle/actions banner used at the top of every feature
 * screen. Primary action buttons are projected in; keeps every list/wizard
 * page's header markup and spacing identical instead of each screen
 * hand-rolling its own <h2>. Also renders a breadcrumb trail above the
 * title, auto-derived from the current route via BreadcrumbService - no
 * per-page wiring needed, every existing call site gets one for free.
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './page-header.component.html',
  styleUrl: './page-header.component.scss'
})
export class PageHeaderComponent {
  private readonly breadcrumbService = inject(BreadcrumbService);

  title = input.required<string>();
  subtitle = input<string | null>(null);

  readonly breadcrumbs = this.breadcrumbService.trail();
}
