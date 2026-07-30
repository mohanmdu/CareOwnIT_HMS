import { Component, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  standalone: true,
  template: `
    <div class="page-header">
      <div class="container">
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="page-header-subtitle">{{ subtitle() }}</p>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .page-header {
        position: relative;
        overflow: hidden;
        padding: var(--space-6) 0 var(--space-5);
        text-align: center;
      }

      .page-header::before {
        content: '';
        position: absolute;
        inset: 0;
        z-index: -1;
        background:
          radial-gradient(60% 120% at 85% 0%, color-mix(in srgb, var(--theme-accent) 14%, transparent) 0%, transparent 60%),
          radial-gradient(70% 130% at 10% 0%, color-mix(in srgb, var(--theme-primary) 12%, transparent) 0%, transparent 65%),
          var(--color-surface);
      }

      .page-header h1 {
        font-size: clamp(1.9rem, 3.4vw, 2.6rem);
      }

      .page-header-subtitle {
        color: var(--color-text-muted);
        max-width: 640px;
        margin: 0 auto;
        font-size: 1.05rem;
      }
    `
  ]
})
export class PageHeaderComponent {
  title = input.required<string>();
  subtitle = input<string | null>(null);
}
