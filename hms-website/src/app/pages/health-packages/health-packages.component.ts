import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PublicHealthPackageService } from '../../core/services/public-health-package.service';
import { PublicHealthPackage } from '../../core/models/public.model';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { IconComponent } from '../../shared/icon/icon.component';

/** Rotating accent palette for package cards - packages are free-text CMS
 * entries with no specialty field to key a color off of (unlike departments),
 * so color is simply assigned by position for visual variety. */
const PACKAGE_COLORS = ['#0f6e5f', '#2f9bd9', '#d98a2b', '#b23d8f', '#7c5cd4', '#e0475f'];

const ICON_KEYWORD_MAP: Array<[RegExp, string]> = [
  [/senior|citizen|elder/i, 'heart-pulse'],
  [/women|woman|maternity|gyneco/i, 'gynecology'],
  [/executive|professional|corporate|premium/i, 'award'],
  [/child|pediatric|paediatric|kid/i, 'baby'],
  [/cardiac|heart/i, 'heart-pulse']
];

@Component({
  selector: 'app-health-packages',
  standalone: true,
  imports: [DecimalPipe, RouterLink, PageHeaderComponent, IconComponent],
  templateUrl: './health-packages.component.html',
  styleUrl: './health-packages.component.scss'
})
export class HealthPackagesComponent {
  private readonly service = inject(PublicHealthPackageService);
  packages = signal<PublicHealthPackage[]>([]);
  loaded = signal(false);

  constructor() {
    this.service.list().subscribe((packages) => {
      this.packages.set(packages);
      this.loaded.set(true);
    });
  }

  colorFor(index: number): string {
    return PACKAGE_COLORS[index % PACKAGE_COLORS.length];
  }

  iconFor(name: string): string {
    const match = ICON_KEYWORD_MAP.find(([pattern]) => pattern.test(name));
    return match ? match[1] : 'shield';
  }

  includesList(includes: string | null): string[] {
    if (!includes) {
      return [];
    }
    return includes
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }
}
