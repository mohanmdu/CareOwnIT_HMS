import { Component, inject, signal } from '@angular/core';
import { PublicSiteContentService } from '../../core/services/public-site-content.service';
import { PublicDepartmentService } from '../../core/services/public-department.service';
import { PublicConsultantService } from '../../core/services/public-consultant.service';
import { PublicTestimonialService } from '../../core/services/public-testimonial.service';
import { PublicSiteContent } from '../../core/models/public.model';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { IconComponent } from '../../shared/icon/icon.component';

@Component({
  selector: 'app-about',
  standalone: true,
  imports: [PageHeaderComponent, IconComponent],
  templateUrl: './about.component.html',
  styleUrl: './about.component.scss'
})
export class AboutComponent {
  private readonly service = inject(PublicSiteContentService);
  private readonly departmentService = inject(PublicDepartmentService);
  private readonly consultantService = inject(PublicConsultantService);
  private readonly testimonialService = inject(PublicTestimonialService);

  content = signal<PublicSiteContent | null>(null);
  departmentCount = signal<number | null>(null);
  doctorCount = signal<number | null>(null);
  /** Real average of actual testimonial ratings, not a fabricated number - null (and hidden in the template) until at least one rated testimonial exists. Mirrors HomeComponent's satisfactionPercent. */
  satisfactionPercent = signal<number | null>(null);

  constructor() {
    this.service.get().subscribe((content) => this.content.set(content));
    this.departmentService.list().subscribe((departments) => this.departmentCount.set(departments.length));
    this.consultantService.list().subscribe((doctors) => this.doctorCount.set(doctors.length));
    this.testimonialService.list().subscribe((testimonials) => {
      const rated = testimonials.filter((t) => t.rating != null);
      if (rated.length > 0) {
        const avg = rated.reduce((sum, t) => sum + (t.rating ?? 0), 0) / rated.length;
        this.satisfactionPercent.set(Math.round((avg / 5) * 100));
      }
    });
  }
}
