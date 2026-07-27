import { Component, input } from '@angular/core';
import { Tone } from '../tone';

export type StatusBadgeTone = Tone;

/**
 * Single consistent "pill" used everywhere a status enum is rendered
 * (Active/Inactive, invoice status, claim status, admission status, ...).
 * Callers only supply a label and a semantic tone - not raw colors - so
 * every status pill in the app draws from the same palette.
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.scss'
})
export class StatusBadgeComponent {
  label = input.required<string>();
  tone = input<StatusBadgeTone>('neutral');
}
