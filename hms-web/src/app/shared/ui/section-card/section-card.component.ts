import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Tone } from '../tone';

/**
 * Generic "card with an icon badge heading" primitive - the one place a
 * page splits into visually distinct topic sections (e.g. Clinic Settings'
 * Hospital Information / Website Configuration / Appearance Settings). Wraps
 * .hms-card-surface rather than duplicating it, so the base card look stays
 * defined in exactly one place (_base.scss).
 */
@Component({
  selector: 'app-section-card',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './section-card.component.html',
  styleUrl: './section-card.component.scss'
})
export class SectionCardComponent {
  icon = input.required<string>();
  title = input.required<string>();
  subtitle = input<string | null>(null);
  tone = input<Tone>('primary');
}
