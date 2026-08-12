import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ClinicSettings } from './clinic-settings.model';

export type ClinicSettingsInput = Pick<
  ClinicSettings,
  | 'name'
  | 'address'
  | 'phone'
  | 'email'
  | 'tinNo'
  | 'dlNo'
  | 'websiteEnabled'
  | 'doctorQueueEnabled'
  | 'domain'
  | 'themePrimaryColor'
  | 'themeSecondaryColor'
  | 'seoDefaultTitle'
  | 'seoDefaultDescription'
  | 'socialFacebookUrl'
  | 'socialInstagramUrl'
  | 'socialYoutubeUrl'
  | 'whatsappNumber'
  | 'themeMode'
  | 'themeTertiaryColor'
  | 'fontFamily'
  | 'cornerRadiusStyle'
  | 'headerBackgroundColor'
  | 'footerBackgroundColor'
  | 'footerText'
  | 'menuBackgroundColor'
  | 'menuTextColor'
  | 'menuActiveBackgroundColor'
  | 'menuActiveTextColor'
  | 'menuHoverBackgroundColor'
  | 'menuIconColor'
  | 'menuChevronColor'
  | 'menuHoverTextColor'
  | 'fontSizeScale'
  | 'brandTextColor'
  | 'menuHoverIconColor'
  | 'animationSpeed'
>;

/**
 * Hospital branding shown on printed receipts - this product deploys to
 * multiple hospital clients, so name/address/logo are admin-editable per
 * deployment here rather than hardcoded in a receipt template.
 */
@Injectable({ providedIn: 'root' })
export class ClinicSettingsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/settings/clinic`;

  get(): Observable<ClinicSettings> {
    return this.http.get<ClinicSettings>(this.baseUrl);
  }

  update(input: ClinicSettingsInput): Observable<ClinicSettings> {
    return this.http.put<ClinicSettings>(this.baseUrl, input);
  }

  uploadLogo(file: File): Observable<ClinicSettings> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ClinicSettings>(`${this.baseUrl}/logo`, formData);
  }

  uploadFavicon(file: File): Observable<ClinicSettings> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ClinicSettings>(`${this.baseUrl}/favicon`, formData);
  }

  uploadLoginBackground(file: File): Observable<ClinicSettings> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ClinicSettings>(`${this.baseUrl}/login-background`, formData);
  }
}
