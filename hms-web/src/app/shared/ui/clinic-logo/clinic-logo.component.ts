import { Component, computed, inject, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { ClinicSettingsService } from '../../../features/masters-admin/clinic-settings/clinic-settings.service';

/**
 * The clinic's uploaded logo image, or a fallback icon when none is set -
 * used by both the app shell brand and the Clinic Settings page's own logo
 * preview, so this fetch/fallback logic lives in exactly one place. Fetches
 * its own copy of ClinicSettings by default; pass `url` when the caller
 * already tracks a fresher value itself (e.g. right after its own upload
 * response), so the preview updates immediately instead of waiting on a
 * fresh fetch this component has no way to know it should trigger.
 */
@Component({
  selector: 'app-clinic-logo',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './clinic-logo.component.html',
  styleUrl: './clinic-logo.component.scss'
})
export class ClinicLogoComponent {
  private readonly clinicSettingsService = inject(ClinicSettingsService);

  /** Square size in px, applied to both the image and the fallback icon. */
  size = input(40);
  /** Known-fresh URL override - when omitted, this component fetches the clinic's current logo itself. */
  url = input<string | null | undefined>(undefined);

  private readonly fetchedLogoUrl = toSignal(
    this.clinicSettingsService.get().pipe(
      map((settings) => settings.logoUrl),
      catchError(() => of(null))
    ),
    { initialValue: null }
  );

  readonly resolvedUrl = computed(() => (this.url() !== undefined ? this.url() : this.fetchedLogoUrl()));
}
