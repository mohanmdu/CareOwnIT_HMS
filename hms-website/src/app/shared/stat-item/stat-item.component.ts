import { Component, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-stat-item',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="stat-item">
      <div class="stat-item-num"><app-icon [name]="icon()" />{{ value() }}</div>
      <div class="stat-item-label">{{ label() }}</div>
    </div>
  `,
  styles: [
    `
      .stat-item {
        text-align: center;
      }
      .stat-item-num {
        font-family: var(--font-heading);
        font-size: clamp(1.9rem, 4vw, 2.5rem);
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.2em;
      }
      .stat-item-label {
        font-size: 0.86rem;
        color: rgba(255, 255, 255, 0.78);
        margin-top: var(--space-2);
      }
    `
  ]
})
export class StatItemComponent {
  icon = input.required<string>();
  value = input.required<string>();
  label = input.required<string>();
}
