import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, of } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Mirrors hms-api's PublicBrandingDto - the narrow, pre-authentication projection shown on the multi-tenant login screen. */
export interface PublicBranding {
  clinicName: string | null;
  logoUrl: string | null;
  loginBackgroundUrl: string | null;
  themePrimaryColor: string | null;
  themeTertiaryColor: string | null;
}

const EMPTY_BRANDING: PublicBranding = {
  clinicName: null,
  logoUrl: null,
  loginBackgroundUrl: null,
  themePrimaryColor: null,
  themeTertiaryColor: null
};

/**
 * The one call in this app made with no JWT at all - see hms-api's
 * PublicBrandingController. Never throws: an unknown client code, a
 * network hiccup, or the field simply being empty all resolve to
 * EMPTY_BRANDING, so the login page always falls back to its generic,
 * unbranded look rather than surfacing an error for what's a cosmetic,
 * best-effort lookup.
 */
@Injectable({ providedIn: 'root' })
export class PublicBrandingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/public/branding`;

  getByClientCode(clientCode: string): Observable<PublicBranding> {
    if (!clientCode.trim()) {
      return of(EMPTY_BRANDING);
    }
    return this.http
      .get<PublicBranding>(this.baseUrl, { params: { clientCode: clientCode.trim() } })
      .pipe(catchError(() => of(EMPTY_BRANDING)));
  }
}
