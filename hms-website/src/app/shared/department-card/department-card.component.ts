import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import { departmentIcon } from '../department-icon/department-icon';
import { PublicDepartment } from '../../core/models/public.model';

@Component({
  selector: 'app-department-card',
  standalone: true,
  imports: [RouterLink, IconComponent],
  template: `
    <a [routerLink]="['/doctors']" [queryParams]="{ departmentId: department().id }" class="card-elevated department-card">
      <span class="service-icon"><app-icon [name]="icon()" /></span>
      <h3>{{ department().name }}</h3>
      <span class="service-link">View doctors <app-icon name="arrow" /></span>
    </a>
  `,
  styles: [
    `
      .department-card {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: var(--space-3);
        text-decoration: none;
        color: var(--color-text);
        padding: var(--space-6) var(--space-5);
      }
      .service-icon {
        width: 84px;
        height: 84px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 2.25rem;
        color: var(--theme-primary);
        background: var(--color-surface-alt);
        transition: transform 0.35s var(--ease);
      }
      .department-card:hover .service-icon {
        transform: scale(1.08) rotate(-4deg);
      }
      .department-card h3 {
        font-size: 1.2rem;
        margin-bottom: 0;
      }
      .service-link {
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
        font-weight: 700;
        font-size: 0.86rem;
        color: var(--theme-primary);
      }
      .service-link app-icon {
        transition: transform 0.3s var(--ease);
      }
      .department-card:hover .service-link app-icon {
        transform: translateX(4px);
      }
    `
  ]
})
export class DepartmentCardComponent {
  department = input.required<PublicDepartment>();
  icon = computed(() => departmentIcon(this.department().name));
}
