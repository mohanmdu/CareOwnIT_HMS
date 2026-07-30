import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PublicConsultant } from '../../core/models/public.model';

@Component({
  selector: 'app-doctor-card',
  standalone: true,
  imports: [RouterLink],
  template: `
    <a [routerLink]="['/doctors', doctor().id]" class="card-elevated doctor-card">
      @if (doctor().imageUrl) {
        <img [src]="doctor().imageUrl" [alt]="doctor().name" class="doctor-photo" />
      } @else {
        <span class="doctor-avatar">{{ initials() }}</span>
      }
      <h4>{{ doctor().name }}</h4>
      @if (doctor().specializationName) {
        <span class="doctor-specialty">{{ doctor().specializationName }}</span>
      }
      <span class="doctor-department">{{ doctor().departmentName }}</span>
    </a>
  `,
  styles: [
    `
      .doctor-card {
        text-align: center;
        text-decoration: none;
        color: var(--color-text);
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-2);
        width: 240px;
      }
      .doctor-photo {
        width: 84px;
        height: 84px;
        border-radius: 50%;
        object-fit: cover;
        margin-bottom: var(--space-2);
        box-shadow: var(--shadow-sm);
      }
      .doctor-avatar {
        width: 84px;
        height: 84px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-family: var(--font-heading);
        font-weight: 700;
        font-size: 1.5rem;
        color: #fff;
        background: linear-gradient(135deg, var(--theme-primary), color-mix(in srgb, var(--theme-primary) 65%, black));
        margin-bottom: var(--space-2);
        box-shadow: var(--shadow-sm);
      }
      .doctor-card h4 {
        font-family: var(--font-body);
        font-size: 1.02rem;
        margin-bottom: 0;
      }
      .doctor-specialty {
        display: inline-block;
        font-size: 0.74rem;
        font-weight: 700;
        color: var(--theme-primary);
        background: var(--color-surface-alt);
        padding: 0.25rem 0.7rem;
        border-radius: var(--radius-pill);
      }
      .doctor-department {
        font-size: 0.82rem;
        color: var(--color-text-faint);
      }
    `
  ]
})
export class DoctorCardComponent {
  doctor = input.required<PublicConsultant>();

  readonly initials = computed(() =>
    this.doctor()
      .name.replace(/^Dr\.?\s+/i, '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join('')
  );
}
