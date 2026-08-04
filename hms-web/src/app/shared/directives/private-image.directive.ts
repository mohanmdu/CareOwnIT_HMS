import { Directive, ElementRef, inject, input, OnChanges, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';

/**
 * Loads an <img> whose source is one of the authenticated "/api/files/..."
 * paths PrivateFileController serves (patient photos, admission photos -
 * see FileStorageService.PRIVATE_CATEGORIES) - a plain [src] binding can't
 * work for these the way it does for public uploads, because a browser
 * never attaches the Authorization header to a raw <img> request. Fetches
 * the image through HttpClient instead (authInterceptor attaches the
 * Bearer token the same as any other API call), then binds an object URL
 * built from the resulting blob.
 *
 * Usage: <img [appPrivateImage]="admission.photoPath" alt="" />
 * A null/undefined path is a no-op - most photo fields are optional.
 */
@Directive({
  selector: 'img[appPrivateImage]',
  standalone: true
})
export class PrivateImageDirective implements OnChanges, OnDestroy {
  readonly appPrivateImage = input<string | null | undefined>();

  private readonly http = inject(HttpClient);
  private readonly el = inject(ElementRef<HTMLImageElement>);
  private objectUrl: string | null = null;

  ngOnChanges(): void {
    this.releaseObjectUrl();
    const path = this.appPrivateImage();
    if (!path) {
      this.el.nativeElement.removeAttribute('src');
      return;
    }
    this.http.get(path, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.objectUrl = URL.createObjectURL(blob);
        this.el.nativeElement.src = this.objectUrl;
      },
      // Leave the <img> without a src on failure (e.g. file not found) -
      // same "just doesn't render" behavior a broken public image URL has.
      error: () => this.el.nativeElement.removeAttribute('src')
    });
  }

  ngOnDestroy(): void {
    this.releaseObjectUrl();
  }

  private releaseObjectUrl(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }
}
