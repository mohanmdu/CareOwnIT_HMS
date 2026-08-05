import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTabsModule } from '@angular/material/tabs';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NotificationService } from '../../../shared/services/notification.service';
import { ClinicLogoComponent } from '../../../shared/ui/clinic-logo/clinic-logo.component';
import { ColorFieldComponent } from '../../../shared/ui/color-field/color-field.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { SectionCardComponent } from '../../../shared/ui/section-card/section-card.component';
import { ThemeService } from '../../../core/services/theme.service';
import {
  CORNER_RADIUS_STYLE_OPTIONS,
  CornerRadiusStyle,
  DEFAULT_THEME_SETTINGS,
  FONT_FAMILY_OPTIONS,
  FONT_SIZE_SCALE_OPTIONS,
  FontSizeScale,
  PRIMARY_THEME_MODE_OPTIONS,
  ThemeMode
} from './clinic-settings.model';
import { ClinicSettingsInput, ClinicSettingsService } from './clinic-settings.service';
import { ThemeFormSnapshot, ThemeHistoryService } from './theme-history.service';
import { parseTheme, serializeTheme } from './theme-json.codec';
import { ThemeMockPreviewComponent } from './theme-mock-preview/theme-mock-preview.component';
import { THEME_PRESETS, ThemePreset, ThemePresetId } from './theme-presets';

@Component({
  selector: 'app-clinic-settings-list',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTabsModule,
    ClinicLogoComponent,
    ColorFieldComponent,
    PageHeaderComponent,
    SectionCardComponent,
    ThemeMockPreviewComponent
  ],
  templateUrl: './clinic-settings-list.component.html',
  styleUrl: './clinic-settings-list.component.scss'
})
export class ClinicSettingsListComponent {
  private readonly service = inject(ClinicSettingsService);
  private readonly notification = inject(NotificationService);
  private readonly themeService = inject(ThemeService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly themeHistory = inject(ThemeHistoryService);

  readonly primaryThemeModeOptions = PRIMARY_THEME_MODE_OPTIONS;
  readonly cornerRadiusStyleOptions = CORNER_RADIUS_STYLE_OPTIONS;
  readonly fontFamilyOptions = FONT_FAMILY_OPTIONS;
  readonly fontSizeScaleOptions = FONT_SIZE_SCALE_OPTIONS;
  readonly themePresets = THEME_PRESETS;

  loading = signal(true);
  saving = signal(false);
  uploadingLogo = signal(false);
  uploadingFavicon = signal(false);
  logoUrl = signal<string | null>(null);
  faviconUrl = signal<string | null>(null);
  showAdvanced = signal(false);
  showJsonPanel = signal(false);
  pasteJsonText = signal('');
  pasteJsonError = signal<string | null>(null);
  private lastExplicitThemeMode: ThemeMode = 'LIGHT';

  form = {
    name: '',
    address: '',
    phone: '',
    email: '',
    tinNo: '',
    dlNo: '',
    websiteEnabled: false,
    doctorQueueEnabled: true,
    domain: '',
    themePrimaryColor: '',
    themeSecondaryColor: '',
    seoDefaultTitle: '',
    seoDefaultDescription: '',
    socialFacebookUrl: '',
    socialInstagramUrl: '',
    socialYoutubeUrl: '',
    whatsappNumber: '',
    themeMode: 'LIGHT' as ThemeMode,
    themeTertiaryColor: '',
    fontFamily: '',
    cornerRadiusStyle: 'ROUNDED' as CornerRadiusStyle,
    headerBackgroundColor: '',
    footerBackgroundColor: '',
    footerText: '',
    menuBackgroundColor: '',
    menuTextColor: '',
    menuActiveBackgroundColor: '',
    menuActiveTextColor: '',
    menuHoverBackgroundColor: '',
    menuIconColor: '',
    menuHoverIconColor: '',
    menuChevronColor: '',
    menuHoverTextColor: '',
    fontSizeScale: 'COMFORTABLE' as FontSizeScale,
    brandTextColor: ''
  };

  constructor() {
    this.service.get().subscribe({
      next: (settings) => {
        this.form = {
          name: settings.name,
          address: settings.address ?? '',
          phone: settings.phone ?? '',
          email: settings.email ?? '',
          tinNo: settings.tinNo ?? '',
          dlNo: settings.dlNo ?? '',
          websiteEnabled: settings.websiteEnabled,
          doctorQueueEnabled: settings.doctorQueueEnabled,
          domain: settings.domain ?? '',
          themePrimaryColor: settings.themePrimaryColor ?? '',
          themeSecondaryColor: settings.themeSecondaryColor ?? '',
          seoDefaultTitle: settings.seoDefaultTitle ?? '',
          seoDefaultDescription: settings.seoDefaultDescription ?? '',
          socialFacebookUrl: settings.socialFacebookUrl ?? '',
          socialInstagramUrl: settings.socialInstagramUrl ?? '',
          socialYoutubeUrl: settings.socialYoutubeUrl ?? '',
          whatsappNumber: settings.whatsappNumber ?? '',
          themeMode: settings.themeMode,
          themeTertiaryColor: settings.themeTertiaryColor ?? '',
          fontFamily: settings.fontFamily ?? '',
          cornerRadiusStyle: settings.cornerRadiusStyle,
          headerBackgroundColor: settings.headerBackgroundColor ?? '',
          footerBackgroundColor: settings.footerBackgroundColor ?? '',
          footerText: settings.footerText ?? '',
          menuBackgroundColor: settings.menuBackgroundColor ?? '',
          menuTextColor: settings.menuTextColor ?? '',
          menuActiveBackgroundColor: settings.menuActiveBackgroundColor ?? '',
          menuActiveTextColor: settings.menuActiveTextColor ?? '',
          menuHoverBackgroundColor: settings.menuHoverBackgroundColor ?? '',
          menuIconColor: settings.menuIconColor ?? '',
          menuHoverIconColor: settings.menuHoverIconColor ?? '',
          menuChevronColor: settings.menuChevronColor ?? '',
          menuHoverTextColor: settings.menuHoverTextColor ?? '',
          fontSizeScale: settings.fontSizeScale,
          brandTextColor: settings.brandTextColor ?? ''
        };
        this.logoUrl.set(settings.logoUrl);
        this.faviconUrl.set(settings.faviconUrl);
        this.loading.set(false);
        this.themeHistory.reset(this.snapshotForm());
      },
      error: () => {
        this.loading.set(false);
        this.notification.error('Failed to load clinic settings.');
      }
    });
  }

  get isValid(): boolean {
    return this.form.name.trim().length > 0;
  }

  /** Whichever preset's fields all match the current form, or null - derived, never imperatively tracked, so manually tweaking a color after picking a preset naturally un-highlights it. */
  get selectedPresetId(): ThemePresetId | null {
    const match = this.themePresets.find(
      (preset) =>
        preset.values.themeMode === this.form.themeMode &&
        preset.values.cornerRadiusStyle === this.form.cornerRadiusStyle &&
        (preset.values.themePrimaryColor ?? '') === this.form.themePrimaryColor &&
        (preset.values.themeSecondaryColor ?? '') === this.form.themeSecondaryColor &&
        (preset.values.themeTertiaryColor ?? '') === this.form.themeTertiaryColor &&
        (preset.values.headerBackgroundColor ?? '') === this.form.headerBackgroundColor &&
        (preset.values.footerBackgroundColor ?? '') === this.form.footerBackgroundColor &&
        (preset.values.menuBackgroundColor ?? '') === this.form.menuBackgroundColor &&
        (preset.values.menuTextColor ?? '') === this.form.menuTextColor &&
        (preset.values.menuActiveBackgroundColor ?? '') === this.form.menuActiveBackgroundColor &&
        (preset.values.menuActiveTextColor ?? '') === this.form.menuActiveTextColor &&
        (preset.values.menuHoverBackgroundColor ?? '') === this.form.menuHoverBackgroundColor &&
        (preset.values.menuHoverTextColor ?? '') === this.form.menuHoverTextColor &&
        (preset.values.menuIconColor ?? '') === this.form.menuIconColor &&
        (preset.values.menuHoverIconColor ?? '') === this.form.menuHoverIconColor &&
        (preset.values.menuChevronColor ?? '') === this.form.menuChevronColor
    );
    return match?.id ?? null;
  }

  /** "Follow my device setting instead" toggle beneath the Light/Dark/Custom selector - maps to themeMode === 'AUTO', remembering the last explicit mode so switching it off restores something sensible. */
  get followSystemTheme(): boolean {
    return this.form.themeMode === 'AUTO';
  }

  set followSystemTheme(value: boolean) {
    if (value) {
      if (this.form.themeMode !== 'AUTO') {
        this.lastExplicitThemeMode = this.form.themeMode;
      }
      this.form.themeMode = 'AUTO';
    } else {
      this.form.themeMode = this.lastExplicitThemeMode;
    }
    this.previewTheme();
  }

  toggleAdvanced(): void {
    this.showAdvanced.update((expanded) => !expanded);
  }

  toggleJsonPanel(): void {
    this.showJsonPanel.update((expanded) => !expanded);
  }

  /** Downloads the in-progress theme as a JSON file - a full personal export (fonts/footer text included), not the narrower shape a curated preset sets. See theme-json.codec.ts. */
  exportTheme(): void {
    const json = serializeTheme(this.snapshotForm());
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const slug = (this.form.name.trim() || 'hms-theme').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
    const link = document.createElement('a');
    link.href = url;
    link.download = `${slug || 'hms-theme'}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  triggerImportFile(input: HTMLInputElement): void {
    input.click();
  }

  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) {
      return;
    }
    file.text().then((text) => this.applyThemeJson(text));
  }

  applyPastedJson(): void {
    this.applyThemeJson(this.pasteJsonText());
  }

  /** Fills the box with a real, valid example (the Healthcare Blue preset's palette, serialized) so an admin can see the exact expected shape instead of guessing at it from the placeholder alone - not a live preview, just reference text they can edit or replace. */
  loadSampleJson(): void {
    const sample = (this.themePresets.find((preset) => preset.id === 'healthcare-blue') ?? this.themePresets[0]).values;
    this.pasteJsonText.set(
      serializeTheme({
        themeMode: sample.themeMode,
        themePrimaryColor: sample.themePrimaryColor ?? '',
        themeSecondaryColor: sample.themeSecondaryColor ?? '',
        themeTertiaryColor: sample.themeTertiaryColor ?? '',
        brandTextColor: '',
        headerBackgroundColor: sample.headerBackgroundColor ?? '',
        footerBackgroundColor: sample.footerBackgroundColor ?? '',
        menuBackgroundColor: sample.menuBackgroundColor ?? '',
        menuTextColor: sample.menuTextColor ?? '',
        menuActiveBackgroundColor: sample.menuActiveBackgroundColor ?? '',
        menuActiveTextColor: sample.menuActiveTextColor ?? '',
        menuHoverBackgroundColor: sample.menuHoverBackgroundColor ?? '',
        menuHoverTextColor: sample.menuHoverTextColor ?? '',
        menuIconColor: sample.menuIconColor ?? '',
        menuHoverIconColor: sample.menuHoverIconColor ?? '',
        menuChevronColor: sample.menuChevronColor ?? ''
      })
    );
    this.pasteJsonError.set(null);
  }

  private applyThemeJson(raw: string): void {
    const result = parseTheme(raw);
    if (!result.ok) {
      this.pasteJsonError.set(result.error);
      this.notification.error(result.error);
      return;
    }
    this.pasteJsonError.set(null);
    this.restoreForm(result.snapshot);
    this.previewTheme();
    this.notification.success('Theme applied - review it below, then Save Changes to keep it.');
  }

  onThemeModeChange(mode: ThemeMode): void {
    this.form.themeMode = mode;
    this.previewTheme();
  }

  /** Applies a curated preset's colors/mode/corner style to the staged form and previews it immediately - does not save, exactly like every other field. */
  applyPreset(preset: ThemePreset): void {
    this.form.themeMode = preset.values.themeMode;
    this.form.cornerRadiusStyle = preset.values.cornerRadiusStyle;
    this.form.themePrimaryColor = preset.values.themePrimaryColor ?? '';
    this.form.themeSecondaryColor = preset.values.themeSecondaryColor ?? '';
    this.form.themeTertiaryColor = preset.values.themeTertiaryColor ?? '';
    this.form.headerBackgroundColor = preset.values.headerBackgroundColor ?? '';
    this.form.footerBackgroundColor = preset.values.footerBackgroundColor ?? '';
    this.form.menuBackgroundColor = preset.values.menuBackgroundColor ?? '';
    this.form.menuTextColor = preset.values.menuTextColor ?? '';
    this.form.menuActiveBackgroundColor = preset.values.menuActiveBackgroundColor ?? '';
    this.form.menuActiveTextColor = preset.values.menuActiveTextColor ?? '';
    this.form.menuHoverBackgroundColor = preset.values.menuHoverBackgroundColor ?? '';
    this.form.menuHoverTextColor = preset.values.menuHoverTextColor ?? '';
    this.form.menuIconColor = preset.values.menuIconColor ?? '';
    this.form.menuHoverIconColor = preset.values.menuHoverIconColor ?? '';
    this.form.menuChevronColor = preset.values.menuChevronColor ?? '';
    this.previewTheme();
  }

  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      return;
    }
    this.uploadingLogo.set(true);
    this.service.uploadLogo(file).subscribe({
      next: (settings) => {
        this.logoUrl.set(settings.logoUrl);
        this.uploadingLogo.set(false);
        this.notification.success('Logo updated.');
      },
      error: () => {
        this.uploadingLogo.set(false);
        this.notification.error('Failed to upload logo.');
      }
    });
  }

  onFaviconSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      return;
    }
    this.uploadingFavicon.set(true);
    this.service.uploadFavicon(file).subscribe({
      next: (settings) => {
        this.faviconUrl.set(settings.faviconUrl);
        this.uploadingFavicon.set(false);
        this.notification.success('Favicon updated.');
      },
      error: () => {
        this.uploadingFavicon.set(false);
        this.notification.error('Failed to upload favicon.');
      }
    });
  }

  save(): void {
    if (!this.isValid) {
      return;
    }
    this.saving.set(true);
    this.service.update(this.buildPayload()).subscribe({
      next: (settings) => {
        this.saving.set(false);
        this.notification.success('Clinic settings saved.');
        this.themeService.applyTheme(settings);
      },
      error: () => {
        this.saving.set(false);
        this.notification.error('Failed to save clinic settings.');
      }
    });
  }

  /** Live preview - applies the in-progress form values immediately, without waiting for Save. Also records this state for Undo. */
  previewTheme(): void {
    const snapshot = this.snapshotForm();
    this.applyThemeSettings(snapshot);
    this.themeHistory.push(snapshot);
  }

  get canUndo(): boolean {
    return this.themeHistory.canUndo();
  }

  /** Steps back to the state before the most recently applied change (a color edit, a preset click, Reset, ...) - does not re-record itself onto the history stack. */
  undo(): void {
    const previous = this.themeHistory.undo();
    if (!previous) {
      return;
    }
    this.restoreForm(previous);
    this.applyThemeSettings(previous);
  }

  private snapshotForm(): ThemeFormSnapshot {
    return {
      themeMode: this.form.themeMode,
      themePrimaryColor: this.form.themePrimaryColor,
      themeSecondaryColor: this.form.themeSecondaryColor,
      themeTertiaryColor: this.form.themeTertiaryColor,
      brandTextColor: this.form.brandTextColor,
      fontFamily: this.form.fontFamily,
      fontSizeScale: this.form.fontSizeScale,
      cornerRadiusStyle: this.form.cornerRadiusStyle,
      headerBackgroundColor: this.form.headerBackgroundColor,
      footerBackgroundColor: this.form.footerBackgroundColor,
      footerText: this.form.footerText,
      menuBackgroundColor: this.form.menuBackgroundColor,
      menuTextColor: this.form.menuTextColor,
      menuActiveBackgroundColor: this.form.menuActiveBackgroundColor,
      menuActiveTextColor: this.form.menuActiveTextColor,
      menuHoverBackgroundColor: this.form.menuHoverBackgroundColor,
      menuHoverTextColor: this.form.menuHoverTextColor,
      menuIconColor: this.form.menuIconColor,
      menuHoverIconColor: this.form.menuHoverIconColor,
      menuChevronColor: this.form.menuChevronColor
    };
  }

  /** Partial on purpose - a JSON-imported snapshot only ever carries themeMode + colors (see ThemeJsonSnapshot), so this must leave fontFamily/fontSizeScale/cornerRadiusStyle/footerText untouched rather than requiring (and defaulting) the full ThemeFormSnapshot shape. Undo still passes a full snapshot, which Object.assign handles identically. */
  private restoreForm(snapshot: Partial<ThemeFormSnapshot>): void {
    Object.assign(this.form, snapshot);
  }

  private applyThemeSettings(snapshot: ThemeFormSnapshot): void {
    this.themeService.applyTheme({
      themePrimaryColor: snapshot.themePrimaryColor || null,
      themeSecondaryColor: snapshot.themeSecondaryColor || null,
      themeTertiaryColor: snapshot.themeTertiaryColor || null,
      fontFamily: snapshot.fontFamily || null,
      cornerRadiusStyle: snapshot.cornerRadiusStyle,
      themeMode: snapshot.themeMode,
      headerBackgroundColor: snapshot.headerBackgroundColor || null,
      footerBackgroundColor: snapshot.footerBackgroundColor || null,
      menuBackgroundColor: snapshot.menuBackgroundColor || null,
      menuTextColor: snapshot.menuTextColor || null,
      menuActiveBackgroundColor: snapshot.menuActiveBackgroundColor || null,
      menuActiveTextColor: snapshot.menuActiveTextColor || null,
      menuHoverBackgroundColor: snapshot.menuHoverBackgroundColor || null,
      menuIconColor: snapshot.menuIconColor || null,
      menuHoverIconColor: snapshot.menuHoverIconColor || null,
      menuChevronColor: snapshot.menuChevronColor || null,
      menuHoverTextColor: snapshot.menuHoverTextColor || null,
      fontSizeScale: snapshot.fontSizeScale,
      brandTextColor: snapshot.brandTextColor || null
    });
  }

  /** Restores the Theme & Appearance section to the application's default look and persists it - hospital name/address/logo/website fields are untouched. */
  resetTheme(): void {
    this.confirmDialog
      .confirm({
        title: 'Reset Theme & Appearance?',
        message:
          'This restores the default theme mode, colors, font, corner style, and header/footer styling, and clears any custom footer text. Hospital name, address, logo, and website settings are not affected.',
        confirmLabel: 'Reset to default',
        destructive: true
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.form.themeMode = DEFAULT_THEME_SETTINGS.themeMode;
        this.form.themePrimaryColor = DEFAULT_THEME_SETTINGS.themePrimaryColor ?? '';
        this.form.themeSecondaryColor = DEFAULT_THEME_SETTINGS.themeSecondaryColor ?? '';
        this.form.themeTertiaryColor = DEFAULT_THEME_SETTINGS.themeTertiaryColor ?? '';
        this.form.fontFamily = DEFAULT_THEME_SETTINGS.fontFamily ?? '';
        this.form.cornerRadiusStyle = DEFAULT_THEME_SETTINGS.cornerRadiusStyle;
        this.form.headerBackgroundColor = DEFAULT_THEME_SETTINGS.headerBackgroundColor ?? '';
        this.form.footerBackgroundColor = DEFAULT_THEME_SETTINGS.footerBackgroundColor ?? '';
        this.form.footerText = DEFAULT_THEME_SETTINGS.footerText ?? '';
        this.form.menuBackgroundColor = DEFAULT_THEME_SETTINGS.menuBackgroundColor ?? '';
        this.form.menuTextColor = DEFAULT_THEME_SETTINGS.menuTextColor ?? '';
        this.form.menuActiveBackgroundColor = DEFAULT_THEME_SETTINGS.menuActiveBackgroundColor ?? '';
        this.form.menuActiveTextColor = DEFAULT_THEME_SETTINGS.menuActiveTextColor ?? '';
        this.form.menuHoverBackgroundColor = DEFAULT_THEME_SETTINGS.menuHoverBackgroundColor ?? '';
        this.form.menuIconColor = DEFAULT_THEME_SETTINGS.menuIconColor ?? '';
        this.form.menuHoverIconColor = DEFAULT_THEME_SETTINGS.menuHoverIconColor ?? '';
        this.form.menuChevronColor = DEFAULT_THEME_SETTINGS.menuChevronColor ?? '';
        this.form.menuHoverTextColor = DEFAULT_THEME_SETTINGS.menuHoverTextColor ?? '';
        this.form.fontSizeScale = DEFAULT_THEME_SETTINGS.fontSizeScale;
        this.form.brandTextColor = DEFAULT_THEME_SETTINGS.brandTextColor ?? '';
        this.previewTheme();

        this.saving.set(true);
        this.service.update(this.buildPayload()).subscribe({
          next: (settings) => {
            this.saving.set(false);
            this.notification.success('Theme & Appearance reset to default.');
            this.themeService.applyTheme(settings);
          },
          error: () => {
            this.saving.set(false);
            this.notification.error('Failed to reset theme.');
          }
        });
      });
  }

  private buildPayload(): ClinicSettingsInput {
    return {
      name: this.form.name.trim(),
      address: this.form.address.trim() || null,
      phone: this.form.phone.trim() || null,
      email: this.form.email.trim() || null,
      tinNo: this.form.tinNo.trim() || null,
      dlNo: this.form.dlNo.trim() || null,
      websiteEnabled: this.form.websiteEnabled,
      doctorQueueEnabled: this.form.doctorQueueEnabled,
      domain: this.form.domain.trim() || null,
      themePrimaryColor: this.form.themePrimaryColor.trim() || null,
      themeSecondaryColor: this.form.themeSecondaryColor.trim() || null,
      seoDefaultTitle: this.form.seoDefaultTitle.trim() || null,
      seoDefaultDescription: this.form.seoDefaultDescription.trim() || null,
      socialFacebookUrl: this.form.socialFacebookUrl.trim() || null,
      socialInstagramUrl: this.form.socialInstagramUrl.trim() || null,
      socialYoutubeUrl: this.form.socialYoutubeUrl.trim() || null,
      whatsappNumber: this.form.whatsappNumber.trim() || null,
      themeMode: this.form.themeMode,
      themeTertiaryColor: this.form.themeTertiaryColor.trim() || null,
      fontFamily: this.form.fontFamily.trim() || null,
      cornerRadiusStyle: this.form.cornerRadiusStyle,
      headerBackgroundColor: this.form.headerBackgroundColor.trim() || null,
      footerBackgroundColor: this.form.footerBackgroundColor.trim() || null,
      footerText: this.form.footerText.trim() || null,
      menuBackgroundColor: this.form.menuBackgroundColor.trim() || null,
      menuTextColor: this.form.menuTextColor.trim() || null,
      menuActiveBackgroundColor: this.form.menuActiveBackgroundColor.trim() || null,
      menuActiveTextColor: this.form.menuActiveTextColor.trim() || null,
      menuHoverBackgroundColor: this.form.menuHoverBackgroundColor.trim() || null,
      menuIconColor: this.form.menuIconColor.trim() || null,
      menuHoverIconColor: this.form.menuHoverIconColor.trim() || null,
      menuChevronColor: this.form.menuChevronColor.trim() || null,
      menuHoverTextColor: this.form.menuHoverTextColor.trim() || null,
      fontSizeScale: this.form.fontSizeScale,
      brandTextColor: this.form.brandTextColor.trim() || null
    };
  }
}
