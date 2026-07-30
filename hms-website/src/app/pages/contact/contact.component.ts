import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteConfigService } from '../../core/services/site-config.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { IconComponent } from '../../shared/icon/icon.component';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent, IconComponent],
  templateUrl: './contact.component.html',
  styleUrl: './contact.component.scss'
})
export class ContactComponent {
  private readonly siteConfig = inject(SiteConfigService);
  config = this.siteConfig.config;

  whatsappLink(number: string): string {
    return `https://wa.me/${number.replace(/\D/g, '')}`;
  }
}
