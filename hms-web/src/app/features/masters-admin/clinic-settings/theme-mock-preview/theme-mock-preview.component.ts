import { Component, computed, input } from '@angular/core';
import { CornerRadiusStyle, ThemeMode } from '../clinic-settings.model';

interface MockPalette {
  primary: string;
  secondary: string;
  tertiary: string;
  surface: string;
  surfaceAlt: string;
  border: string;
  text: string;
  textMuted: string;
  primaryContainer: string;
  onPrimaryContainer: string;
}

/**
 * Mirrors styles/_tokens.scss's light/dark default hex values - intentional,
 * documented duplication (same spirit as ThemeService's MATERIAL_PRIMARY_VARS
 * comment) so a preset card can render its own look regardless of whatever
 * theme is actually live on the page around it.
 */
const LIGHT_FALLBACK: MockPalette = {
  primary: '#0b5fa6',
  secondary: '#6a4fb0',
  tertiary: '#0e8074',
  surface: '#ffffff',
  surfaceAlt: '#f6f8fa',
  border: '#dde3ea',
  text: '#1b1f24',
  textMuted: '#5b6673',
  primaryContainer: '#e3eefb',
  onPrimaryContainer: '#063f6e'
};

const DARK_FALLBACK: MockPalette = {
  primary: '#6fa8dc',
  secondary: '#b39ddb',
  tertiary: '#4fbfae',
  surface: '#16181d',
  surfaceAlt: '#1d2027',
  border: '#2c313a',
  text: '#e7e9ec',
  textMuted: '#a1a9b4',
  primaryContainer: '#123a5c',
  onPrimaryContainer: '#cfe4f7'
};

const RADIUS_MAP: Record<CornerRadiusStyle, string> = {
  SQUARE: '2px',
  ROUNDED: '8px',
  PILL: '999px'
};

/**
 * Small, purely presentational mock "app window" (header + sidebar + content)
 * used both by the preset gallery cards and the Live Preview panel in
 * Appearance Settings. Driven entirely by its own inputs - never by
 * inheriting the page's live --hms-* variables - since several of these must
 * render different looks side by side (the 6 preset cards) or reflect
 * in-progress/unsaved form values (the Live Preview panel).
 */
@Component({
  selector: 'app-theme-mock-preview',
  standalone: true,
  templateUrl: './theme-mock-preview.component.html',
  styleUrl: './theme-mock-preview.component.scss'
})
export class ThemeMockPreviewComponent {
  themeMode = input<ThemeMode>('LIGHT');
  primaryColor = input<string | null>(null);
  secondaryColor = input<string | null>(null);
  tertiaryColor = input<string | null>(null);
  headerBackgroundColor = input<string | null>(null);
  menuBackgroundColor = input<string | null>(null);
  menuTextColor = input<string | null>(null);
  menuActiveBackgroundColor = input<string | null>(null);
  menuActiveTextColor = input<string | null>(null);
  menuIconColor = input<string | null>(null);
  cornerRadiusStyle = input<CornerRadiusStyle>('ROUNDED');
  size = input<'sm' | 'lg'>('sm');

  private readonly palette = computed<MockPalette>(() => (this.themeMode() === 'DARK' ? DARK_FALLBACK : LIGHT_FALLBACK));

  resolvedPrimary = computed(() => this.primaryColor() || this.palette().primary);
  resolvedSecondary = computed(() => this.secondaryColor() || this.palette().secondary);
  resolvedTertiary = computed(() => this.tertiaryColor() || this.palette().tertiary);
  resolvedHeaderBg = computed(() => this.headerBackgroundColor() || this.palette().surface);
  resolvedContentBg = computed(() => this.palette().surfaceAlt);
  resolvedCardBg = computed(() => this.palette().surface);
  resolvedBorder = computed(() => this.palette().border);
  resolvedMenuBg = computed(() => this.menuBackgroundColor() || this.palette().surface);
  resolvedMenuText = computed(() => this.menuTextColor() || this.palette().text);
  resolvedMenuActiveBg = computed(() => this.menuActiveBackgroundColor() || this.palette().primaryContainer);
  resolvedMenuActiveText = computed(() => this.menuActiveTextColor() || this.palette().onPrimaryContainer);
  resolvedMenuIcon = computed(() => this.menuIconColor() || this.palette().textMuted);
  resolvedIconRadius = computed(() => RADIUS_MAP[this.cornerRadiusStyle()]);
}
