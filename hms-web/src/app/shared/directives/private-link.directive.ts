import { Directive, HostListener, inject, input } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NotificationService } from '../services/notification.service';

/**
 * Opens one of the authenticated "/api/files/..." documents
 * PrivateFileController serves (patient report PDFs - see
 * FileStorageService.PRIVATE_CATEGORIES) in a new tab - the anchor-tag
 * counterpart to PrivateImageDirective, needed for the same reason: a plain
 * <a href target="_blank"> can't carry the Authorization header a browser
 * needs to reach an authenticated endpoint.
 *
 * Usage: <a [appPrivateLink]="report.filePath">View</a> - the href isn't
 * used for navigation (the click is intercepted), only kept so the link
 * still looks and behaves like a link (right-click "copy link" excluded,
 * an acceptable trade-off for an authenticated resource with no stable
 * public URL to copy anyway).
 */
@Directive({
  selector: 'a[appPrivateLink]',
  standalone: true
})
export class PrivateLinkDirective {
  readonly appPrivateLink = input<string | null | undefined>();

  private readonly http = inject(HttpClient);
  private readonly notification = inject(NotificationService);

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    const path = this.appPrivateLink();
    if (!path) {
      return;
    }
    event.preventDefault();
    this.http.get(path, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        // The opened tab has already loaded the blob by the time this fires -
        // long enough to be safe without leaking the object URL forever.
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.notification.error('Failed to open this file.')
    });
  }
}
