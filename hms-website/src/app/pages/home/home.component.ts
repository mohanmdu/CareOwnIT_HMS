import { isPlatformBrowser } from '@angular/common';
import { Component, inject, OnDestroy, PLATFORM_ID, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PublicBannerSlideService } from '../../core/services/public-banner-slide.service';
import { PublicSiteContentService } from '../../core/services/public-site-content.service';
import { PublicNewsEventService } from '../../core/services/public-news-event.service';
import { PublicTestimonialService } from '../../core/services/public-testimonial.service';
import { PublicDepartmentService } from '../../core/services/public-department.service';
import { PublicConsultantService } from '../../core/services/public-consultant.service';
import { SiteConfigService } from '../../core/services/site-config.service';
import {
  PublicBannerSlide,
  PublicConsultant,
  PublicDepartment,
  PublicNewsEvent,
  PublicSiteContent,
  PublicTestimonial
} from '../../core/models/public.model';
import { IconComponent } from '../../shared/icon/icon.component';
import { StatItemComponent } from '../../shared/stat-item/stat-item.component';
import { DepartmentCardComponent } from '../../shared/department-card/department-card.component';
import { DoctorCardComponent } from '../../shared/doctor-card/doctor-card.component';
import { TestimonialCardComponent } from '../../shared/testimonial-card/testimonial-card.component';

/** How many departments/doctors to feature on the home page before "View all". */
const HOME_PREVIEW_COUNT = 6;
const HOME_DOCTOR_PREVIEW_COUNT = 4;

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, IconComponent, StatItemComponent, DepartmentCardComponent, DoctorCardComponent, TestimonialCardComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnDestroy {
  private readonly bannerService = inject(PublicBannerSlideService);
  private readonly siteContentService = inject(PublicSiteContentService);
  private readonly newsService = inject(PublicNewsEventService);
  private readonly testimonialService = inject(PublicTestimonialService);
  private readonly departmentService = inject(PublicDepartmentService);
  private readonly consultantService = inject(PublicConsultantService);
  private readonly siteConfig = inject(SiteConfigService);
  private readonly platformId = inject(PLATFORM_ID);
  private rotationTimer?: ReturnType<typeof setInterval>;

  readonly config = this.siteConfig.config;

  slides = signal<PublicBannerSlide[]>([]);
  activeSlide = signal(0);
  siteContent = signal<PublicSiteContent | null>(null);
  news = signal<PublicNewsEvent[]>([]);
  testimonials = signal<PublicTestimonial[]>([]);
  departments = signal<PublicDepartment[]>([]);
  doctors = signal<PublicConsultant[]>([]);
  departmentsLoaded = signal(false);
  /** Real average of actual testimonial ratings, not a fabricated number - null (and hidden in the template) until at least one rated testimonial exists. */
  satisfactionPercent = signal<number | null>(null);

  readonly homeDepartments = () => this.departments().slice(0, HOME_PREVIEW_COUNT);
  readonly homeDoctors = () => this.doctors().slice(0, HOME_DOCTOR_PREVIEW_COUNT);

  constructor() {
    this.bannerService.list().subscribe((slides) => {
      this.slides.set(slides);
      if (isPlatformBrowser(this.platformId) && slides.length > 1) {
        this.rotationTimer = setInterval(() => this.next(), 6000);
      }
    });
    this.siteContentService.get().subscribe((content) => this.siteContent.set(content));
    this.newsService.list().subscribe((news) => this.news.set(news.slice(0, 3)));
    this.testimonialService.list().subscribe((testimonials) => {
      this.testimonials.set(testimonials.slice(0, 3));
      const rated = testimonials.filter((t) => t.rating != null);
      if (rated.length > 0) {
        const avg = rated.reduce((sum, t) => sum + (t.rating ?? 0), 0) / rated.length;
        this.satisfactionPercent.set(Math.round((avg / 5) * 100));
      }
    });
    this.departmentService.list().subscribe((departments) => {
      this.departments.set(departments);
      this.departmentsLoaded.set(true);
    });
    this.consultantService.list().subscribe((doctors) => this.doctors.set(doctors));
  }

  next(): void {
    const total = this.slides().length;
    if (total > 0) {
      this.activeSlide.set((this.activeSlide() + 1) % total);
    }
  }

  previous(): void {
    const total = this.slides().length;
    if (total > 0) {
      this.activeSlide.set((this.activeSlide() - 1 + total) % total);
    }
  }

  goTo(index: number): void {
    this.activeSlide.set(index);
  }

  ngOnDestroy(): void {
    if (this.rotationTimer) {
      clearInterval(this.rotationTimer);
    }
  }
}
