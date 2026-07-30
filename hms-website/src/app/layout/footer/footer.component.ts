import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteConfigService } from '../../core/services/site-config.service';
import { IconComponent } from '../../shared/icon/icon.component';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink, IconComponent],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss'
})
export class FooterComponent {
  private readonly siteConfig = inject(SiteConfigService);
  readonly config = this.siteConfig.config;
  readonly currentYear = new Date().getFullYear();

  whatsappLink(number: string): string {
    return `https://wa.me/${number.replace(/\D/g, '')}`;
  }
}
