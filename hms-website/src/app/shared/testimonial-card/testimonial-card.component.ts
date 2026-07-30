import { Component, computed, input } from '@angular/core';
import { PublicTestimonial } from '../../core/models/public.model';

@Component({
  selector: 'app-testimonial-card',
  standalone: true,
  template: `
    <div class="card-elevated testimonial-card">
      <span class="quote-mark">&ldquo;</span>
      @if (stars() > 0) {
        <div class="testimonial-stars" [attr.aria-label]="stars() + ' out of 5 stars'">
          @for (i of starRange; track i) {
            <span [class.filled]="i < stars()">★</span>
          }
        </div>
      }
      <p class="testimonial-text">{{ testimonial().quote }}</p>
      <div class="testimonial-person">
        @if (testimonial().imageUrl) {
          <img [src]="testimonial().imageUrl" [alt]="testimonial().patientName" class="testimonial-avatar-photo" />
        } @else {
          <span class="testimonial-avatar">{{ initials() }}</span>
        }
        <strong>{{ testimonial().patientName }}</strong>
      </div>
    </div>
  `,
  styles: [
    `
      .testimonial-card {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .quote-mark {
        font-family: var(--font-heading);
        font-size: 3rem;
        line-height: 0.6;
        color: var(--theme-accent);
        opacity: 0.6;
      }
      .testimonial-stars {
        color: var(--color-border);
        font-size: 0.95rem;
        letter-spacing: 0.12em;
      }
      .testimonial-stars .filled {
        color: var(--theme-accent);
      }
      .testimonial-text {
        color: var(--color-text-muted);
        font-size: 0.98rem;
        margin: 0;
        flex: 1;
      }
      .testimonial-person {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        margin-top: auto;
      }
      .testimonial-avatar,
      .testimonial-avatar-photo {
        width: 42px;
        height: 42px;
        border-radius: 50%;
        flex-shrink: 0;
      }
      .testimonial-avatar-photo {
        object-fit: cover;
      }
      .testimonial-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        font-family: var(--font-heading);
        font-weight: 700;
        font-size: 0.9rem;
        color: #fff;
        background: linear-gradient(135deg, var(--theme-accent), var(--theme-accent-dark));
      }
    `
  ]
})
export class TestimonialCardComponent {
  testimonial = input.required<PublicTestimonial>();

  readonly starRange = [0, 1, 2, 3, 4];
  readonly stars = computed(() => Math.round(this.testimonial().rating ?? 0));

  readonly initials = computed(() =>
    this.testimonial()
      .patientName.split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join('')
  );
}
