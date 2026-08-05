import { inject, Injectable } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ClinicSettingsService } from '../../features/masters-admin/clinic-settings/clinic-settings.service';
import { CornerRadiusStyle, FontSizeScale, ThemeMode } from '../../features/masters-admin/clinic-settings/clinic-settings.model';
import { bumpChartThemeVersion } from './chart-theme.signal';

const DEFAULT_FAVICON_URL = 'favicon.ico';

export interface ThemeSettings {
  themePrimaryColor?: string | null;
  themeSecondaryColor?: string | null;
  themeTertiaryColor?: string | null;
  fontFamily?: string | null;
  cornerRadiusStyle?: CornerRadiusStyle;
  themeMode?: ThemeMode;
  headerBackgroundColor?: string | null;
  footerBackgroundColor?: string | null;
  menuBackgroundColor?: string | null;
  menuTextColor?: string | null;
  menuActiveBackgroundColor?: string | null;
  menuActiveTextColor?: string | null;
  menuHoverBackgroundColor?: string | null;
  menuIconColor?: string | null;
  menuChevronColor?: string | null;
  menuHoverTextColor?: string | null;
  fontSizeScale?: FontSizeScale;
  brandTextColor?: string | null;
  menuHoverIconColor?: string | null;
  faviconUrl?: string | null;
}

/**
 * Angular Material's M3 theme (see styles/_theme.scss) doesn't expose one
 * small "--mat-sys-*" token layer in this Material version - it emits a
 * fully-resolved hex value per component sub-property instead. These are
 * every --mat- and --mdc- prefixed custom property confirmed (via the compiled
 * production CSS) to hold the theme's resolved primary color - overriding
 * all of them is how the brand color actually reaches Material chrome
 * (buttons, checkboxes, radios, switches, tabs, sliders, text fields,
 * stepper, datepicker) without a rebuild. This list only covers the
 * *interactive primary* role - error/disabled/neutral colors are
 * deliberately left alone.
 */
const MATERIAL_PRIMARY_VARS: readonly string[] = [
  '--mat-datepicker-calendar-date-preview-state-outline-color',
  '--mat-datepicker-calendar-date-selected-state-background-color',
  '--mat-datepicker-calendar-date-today-outline-color',
  '--mat-datepicker-calendar-date-today-selected-state-outline-color',
  '--mat-form-field-focus-select-arrow-color',
  '--mat-full-pseudo-checkbox-selected-icon-color',
  '--mat-minimal-pseudo-checkbox-selected-checkmark-color',
  '--mat-outlined-button-state-layer-color',
  '--mat-protected-button-state-layer-color',
  '--mat-radio-checked-ripple-color',
  '--mat-select-focused-arrow-color',
  '--mat-slider-ripple-color',
  '--mat-stepper-header-edit-state-icon-background-color',
  '--mat-stepper-header-selected-state-icon-background-color',
  '--mat-tab-header-active-focus-indicator-color',
  '--mat-tab-header-active-hover-indicator-color',
  '--mat-text-button-state-layer-color',
  '--mdc-checkbox-selected-focus-icon-color',
  '--mdc-checkbox-selected-focus-state-layer-color',
  '--mdc-checkbox-selected-hover-icon-color',
  '--mdc-checkbox-selected-hover-state-layer-color',
  '--mdc-checkbox-selected-icon-color',
  '--mdc-checkbox-selected-pressed-icon-color',
  '--mdc-checkbox-unselected-pressed-state-layer-color',
  '--mdc-circular-progress-active-indicator-color',
  '--mdc-filled-button-container-color',
  '--mdc-filled-text-field-caret-color',
  '--mdc-filled-text-field-focus-active-indicator-color',
  '--mdc-filled-text-field-focus-label-text-color',
  '--mdc-linear-progress-active-indicator-color',
  '--mdc-list-list-item-selected-trailing-icon-color',
  '--mdc-outlined-button-label-text-color',
  '--mdc-outlined-text-field-caret-color',
  '--mdc-outlined-text-field-focus-label-text-color',
  '--mdc-outlined-text-field-focus-outline-color',
  '--mdc-protected-button-label-text-color',
  '--mdc-radio-selected-focus-icon-color',
  '--mdc-radio-selected-hover-icon-color',
  '--mdc-radio-selected-icon-color',
  '--mdc-radio-selected-pressed-icon-color',
  '--mdc-slider-active-track-color',
  '--mdc-slider-focus-handle-color',
  '--mdc-slider-handle-color',
  '--mdc-slider-hover-handle-color',
  '--mdc-slider-label-container-color',
  '--mdc-switch-selected-focus-state-layer-color',
  '--mdc-switch-selected-focus-track-color',
  '--mdc-switch-selected-hover-state-layer-color',
  '--mdc-switch-selected-hover-track-color',
  '--mdc-switch-selected-pressed-state-layer-color',
  '--mdc-switch-selected-pressed-track-color',
  '--mdc-switch-selected-track-color',
  '--mdc-tab-indicator-active-indicator-color',
  '--mdc-text-button-label-text-color'
];

const CORNER_RADIUS_MAP: Record<CornerRadiusStyle, { sm: string; md: string; lg: string }> = {
  SQUARE: { sm: '2px', md: '2px', lg: '4px' },
  ROUNDED: { sm: '4px', md: '8px', lg: '12px' },
  PILL: { sm: '999px', md: '999px', lg: '999px' }
};

const FONT_SCALE_MAP: Record<FontSizeScale, string> = {
  COMPACT: '0.9',
  COMFORTABLE: '1',
  SPACIOUS: '1.1'
};

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly clinicSettingsService = inject(ClinicSettingsService);

  /** Called once from APP_INITIALIZER, before the shell renders - never blocks bootstrap on failure. */
  loadInitialTheme(): Promise<void> {
    return new Promise((resolve) => {
      this.clinicSettingsService
        .get()
        .pipe(catchError(() => of(null)))
        .subscribe((settings) => {
          if (settings) {
            this.applyTheme(settings);
          }
          resolve();
        });
    });
  }

  applyTheme(settings: ThemeSettings): void {
    const root = document.documentElement.style;

    if (settings.themePrimaryColor) {
      root.setProperty('--hms-color-primary', settings.themePrimaryColor);
      root.setProperty('--hms-color-info', settings.themePrimaryColor);
      for (const cssVar of MATERIAL_PRIMARY_VARS) {
        root.setProperty(cssVar, settings.themePrimaryColor);
      }
    } else {
      root.removeProperty('--hms-color-primary');
      root.removeProperty('--hms-color-info');
      for (const cssVar of MATERIAL_PRIMARY_VARS) {
        root.removeProperty(cssVar);
      }
    }
    if (settings.themeTertiaryColor) {
      root.setProperty('--hms-color-tertiary', settings.themeTertiaryColor);
    } else {
      root.removeProperty('--hms-color-tertiary');
    }
    if (settings.themeSecondaryColor) {
      root.setProperty('--hms-color-secondary', settings.themeSecondaryColor);
    } else {
      root.removeProperty('--hms-color-secondary');
    }
    if (settings.fontFamily) {
      root.setProperty('--hms-font-family', settings.fontFamily);
    } else {
      root.removeProperty('--hms-font-family');
    }
    if (settings.headerBackgroundColor) {
      root.setProperty('--hms-header-bg', settings.headerBackgroundColor);
    } else {
      root.removeProperty('--hms-header-bg');
    }
    if (settings.footerBackgroundColor) {
      root.setProperty('--hms-footer-bg', settings.footerBackgroundColor);
    } else {
      root.removeProperty('--hms-footer-bg');
    }
    if (settings.menuBackgroundColor) {
      root.setProperty('--hms-menu-bg', settings.menuBackgroundColor);
    } else {
      root.removeProperty('--hms-menu-bg');
    }
    if (settings.menuTextColor) {
      root.setProperty('--hms-menu-text', settings.menuTextColor);
    } else {
      root.removeProperty('--hms-menu-text');
    }
    if (settings.menuActiveBackgroundColor) {
      root.setProperty('--hms-menu-active-bg', settings.menuActiveBackgroundColor);
    } else {
      root.removeProperty('--hms-menu-active-bg');
    }
    if (settings.menuActiveTextColor) {
      root.setProperty('--hms-menu-active-text', settings.menuActiveTextColor);
    } else {
      root.removeProperty('--hms-menu-active-text');
    }
    if (settings.menuHoverBackgroundColor) {
      root.setProperty('--hms-menu-hover-bg', settings.menuHoverBackgroundColor);
    } else {
      root.removeProperty('--hms-menu-hover-bg');
    }
    if (settings.menuIconColor) {
      root.setProperty('--hms-menu-icon', settings.menuIconColor);
    } else {
      root.removeProperty('--hms-menu-icon');
    }
    if (settings.menuChevronColor) {
      root.setProperty('--hms-menu-chevron', settings.menuChevronColor);
    } else {
      root.removeProperty('--hms-menu-chevron');
    }
    if (settings.menuHoverTextColor) {
      root.setProperty('--hms-menu-hover-text', settings.menuHoverTextColor);
    } else {
      root.removeProperty('--hms-menu-hover-text');
    }
    if (settings.brandTextColor) {
      root.setProperty('--hms-brand-text', settings.brandTextColor);
    } else {
      root.removeProperty('--hms-brand-text');
    }
    if (settings.menuHoverIconColor) {
      root.setProperty('--hms-menu-hover-icon', settings.menuHoverIconColor);
    } else {
      root.removeProperty('--hms-menu-hover-icon');
    }

    const radii = CORNER_RADIUS_MAP[settings.cornerRadiusStyle ?? 'ROUNDED'];
    root.setProperty('--hms-radius-sm', radii.sm);
    root.setProperty('--hms-radius-md', radii.md);
    root.setProperty('--hms-radius-lg', radii.lg);

    root.setProperty('--hms-font-scale', FONT_SCALE_MAP[settings.fontSizeScale ?? 'COMFORTABLE']);

    this.applyThemeMode(settings.themeMode);
    this.applyFavicon(settings);
    bumpChartThemeVersion();
  }

  /**
   * Swaps the browser tab icon at runtime - faviconUrl is already stored/
   * uploadable (see ClinicSettingsService), this was the missing last step
   * that actually applies it. Falls back to the compiled-in default rather
   * than removing the tag, so a client that never uploaded one keeps the
   * app's own icon instead of a blank tab.
   *
   * Checked via 'in', not just reading the property: the live-preview path
   * (clinic-settings-list.component.ts's applyThemeSettings) sends a
   * partial ThemeSettings with no faviconUrl key at all (favicon uploads
   * apply immediately, they're not part of the color-preview draft) - a
   * plain undefined check couldn't tell that apart from "clear the
   * favicon", and would wipe out the tenant's real uploaded favicon on
   * every single color tweak made while previewing.
   */
  private applyFavicon(settings: ThemeSettings): void {
    if (!('faviconUrl' in settings)) {
      return;
    }
    const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (link) {
      link.href = settings.faviconUrl ?? DEFAULT_FAVICON_URL;
    }
  }

  private applyThemeMode(mode: ThemeMode | undefined): void {
    const root = document.documentElement;
    switch (mode) {
      case 'DARK':
        root.dataset['theme'] = 'dark';
        break;
      case 'LIGHT':
      case 'CUSTOM':
        root.dataset['theme'] = 'light';
        break;
      case 'AUTO':
      default:
        delete root.dataset['theme'];
    }
  }
}
