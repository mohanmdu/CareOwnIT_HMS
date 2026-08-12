export type ThemeMode = 'LIGHT' | 'DARK' | 'CUSTOM' | 'AUTO';
export type CornerRadiusStyle = 'SQUARE' | 'ROUNDED' | 'PILL';
export type FontSizeScale = 'COMPACT' | 'COMFORTABLE' | 'SPACIOUS';
export type AnimationSpeed = 'NONE' | 'SUBTLE' | 'STANDARD';

export const THEME_MODE_OPTIONS: { value: ThemeMode; label: string }[] = [
  { value: 'LIGHT', label: 'Light' },
  { value: 'DARK', label: 'Dark' },
  { value: 'CUSTOM', label: 'Custom (full branding)' },
  { value: 'AUTO', label: 'Follow system' }
];

/** The 3 primary modes shown in the Appearance Settings mode selector - AUTO is offered separately as a "follow system" toggle instead of a 4th equal option. */
export const PRIMARY_THEME_MODE_OPTIONS = THEME_MODE_OPTIONS.filter((option) => option.value !== 'AUTO');

export const CORNER_RADIUS_STYLE_OPTIONS: { value: CornerRadiusStyle; label: string }[] = [
  { value: 'SQUARE', label: 'Square' },
  { value: 'ROUNDED', label: 'Rounded' },
  { value: 'PILL', label: 'Pill' }
];

export const FONT_SIZE_SCALE_OPTIONS: { value: FontSizeScale; label: string }[] = [
  { value: 'COMPACT', label: 'Compact' },
  { value: 'COMFORTABLE', label: 'Comfortable (default)' },
  { value: 'SPACIOUS', label: 'Spacious' }
];

/** Controls --hms-transition-fast/--hms-transition-base (see ThemeService) - hover/active states, sidenav expand, menu highlights. NONE disables every CSS transition that references those two tokens; it doesn't touch Angular Material's own component animations (menus, dialogs) or the JSON-imported color palette. */
export const ANIMATION_SPEED_OPTIONS: { value: AnimationSpeed; label: string }[] = [
  { value: 'NONE', label: 'None' },
  { value: 'SUBTLE', label: 'Subtle' },
  { value: 'STANDARD', label: 'Standard (default)' }
];

/** Curated allowlist, not free text - see ThemeService for why. */
export const FONT_FAMILY_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: 'Default' },
  { value: "system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif", label: 'System (default)' },
  { value: "Georgia, 'Times New Roman', serif", label: 'Georgia (serif)' },
  { value: "'Trebuchet MS', Verdana, sans-serif", label: 'Trebuchet MS' },
  { value: "'Courier New', monospace", label: 'Courier New (monospace)' }
];

/**
 * The application's out-of-the-box theme - restored by "Reset Theme & Appearance".
 * Colors/font are null (inherit the compiled stylesheet defaults in
 * styles/_tokens.scss) rather than hardcoded hex values, so this reset stays
 * correct even if that palette changes later; themeMode/cornerRadiusStyle
 * are non-nullable columns and get their DB/entity default explicitly.
 */
export const DEFAULT_THEME_SETTINGS: Pick<
  ClinicSettings,
  | 'themeMode'
  | 'themePrimaryColor'
  | 'themeSecondaryColor'
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
> = {
  themeMode: 'LIGHT',
  themePrimaryColor: null,
  themeSecondaryColor: null,
  themeTertiaryColor: null,
  fontFamily: null,
  cornerRadiusStyle: 'ROUNDED',
  headerBackgroundColor: null,
  footerBackgroundColor: null,
  footerText: null,
  menuBackgroundColor: null,
  menuTextColor: null,
  menuActiveBackgroundColor: null,
  menuActiveTextColor: null,
  menuHoverBackgroundColor: null,
  menuIconColor: null,
  menuChevronColor: null,
  menuHoverTextColor: null,
  fontSizeScale: 'COMFORTABLE',
  brandTextColor: null,
  menuHoverIconColor: null,
  animationSpeed: 'STANDARD'
};

export interface ClinicSettings {
  name: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  logoUrl: string | null;
  tinNo: string | null;
  dlNo: string | null;
  websiteEnabled: boolean;
  doctorQueueEnabled: boolean;
  domain: string | null;
  themePrimaryColor: string | null;
  themeSecondaryColor: string | null;
  faviconUrl: string | null;
  seoDefaultTitle: string | null;
  seoDefaultDescription: string | null;
  socialFacebookUrl: string | null;
  socialInstagramUrl: string | null;
  socialYoutubeUrl: string | null;
  whatsappNumber: string | null;
  themeMode: ThemeMode;
  themeTertiaryColor: string | null;
  fontFamily: string | null;
  cornerRadiusStyle: CornerRadiusStyle;
  headerBackgroundColor: string | null;
  footerBackgroundColor: string | null;
  footerText: string | null;
  menuBackgroundColor: string | null;
  menuTextColor: string | null;
  menuActiveBackgroundColor: string | null;
  menuActiveTextColor: string | null;
  menuHoverBackgroundColor: string | null;
  menuIconColor: string | null;
  menuChevronColor: string | null;
  menuHoverTextColor: string | null;
  fontSizeScale: FontSizeScale;
  /** Independent from themePrimaryColor - the hospital name shown top-left in the side menu (see app-shell.component.scss's .shell-brand). */
  brandTextColor: string | null;
  /** Independent from menuIconColor - what a leaf nav item's icon becomes on hover. */
  menuHoverIconColor: string | null;
  /** Shown pre-authentication on the multi-tenant login screen - see PublicBrandingService (hms-api) and core/services/public-branding.service.ts. */
  loginBackgroundUrl: string | null;
  animationSpeed: AnimationSpeed;
}
