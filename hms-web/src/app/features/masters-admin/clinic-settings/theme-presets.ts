import { ClinicSettings } from './clinic-settings.model';

export type ThemePresetId = 'indigo' | 'teal' | 'purple' | 'emerald' | 'modern-light' | 'dark';

/**
 * The subset of ClinicSettings a preset can set in one click. Deliberately
 * excludes fontFamily/footerText/fontSizeScale - picking a preset should
 * never clobber typography or copy an admin may have already set separately.
 */
export type ThemePresetValues = Pick<
  ClinicSettings,
  | 'themeMode'
  | 'cornerRadiusStyle'
  | 'themePrimaryColor'
  | 'themeSecondaryColor'
  | 'themeTertiaryColor'
  | 'headerBackgroundColor'
  | 'footerBackgroundColor'
  | 'menuBackgroundColor'
  | 'menuTextColor'
  | 'menuActiveBackgroundColor'
  | 'menuActiveTextColor'
  | 'menuHoverBackgroundColor'
  | 'menuHoverTextColor'
  | 'menuIconColor'
  | 'menuChevronColor'
>;

export interface ThemePreset {
  id: ThemePresetId;
  label: string;
  caption: string;
  values: ThemePresetValues;
}

/**
 * Curated, hand-picked palettes (no runtime color math) - each hue is a
 * well-documented Tailwind-scale shade chosen to keep white sidebar text/icons
 * at an accessible contrast ratio. Header/footer background stay null for
 * every preset so the header stays light, matching the reference design
 * (only the sidebar itself picks up the bold preset color).
 */
export const THEME_PRESETS: ThemePreset[] = [
  {
    id: 'indigo',
    label: 'Indigo Gradient',
    caption: 'Bold indigo sidebar with a teal accent.',
    values: {
      themeMode: 'CUSTOM',
      cornerRadiusStyle: 'ROUNDED',
      themePrimaryColor: '#4F46E5',
      themeSecondaryColor: '#6366F1',
      themeTertiaryColor: '#14B8A6',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#4338CA',
      menuTextColor: '#E0E7FF',
      menuActiveBackgroundColor: '#6366F1',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#4F46E5',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#C7D2FE',
      menuChevronColor: '#A5B4FC'
    }
  },
  {
    id: 'teal',
    label: 'Teal',
    caption: 'Calm teal sidebar with a warm amber accent.',
    values: {
      themeMode: 'CUSTOM',
      cornerRadiusStyle: 'ROUNDED',
      themePrimaryColor: '#0D9488',
      themeSecondaryColor: '#0891B2',
      themeTertiaryColor: '#D97706',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#115E59',
      menuTextColor: '#CCFBF1',
      menuActiveBackgroundColor: '#0D9488',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#0F766E',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#99F6E4',
      menuChevronColor: '#5EEAD4'
    }
  },
  {
    id: 'purple',
    label: 'Purple',
    caption: 'Rich purple sidebar with a pink accent.',
    values: {
      themeMode: 'CUSTOM',
      cornerRadiusStyle: 'ROUNDED',
      themePrimaryColor: '#7C3AED',
      themeSecondaryColor: '#A855F7',
      themeTertiaryColor: '#EC4899',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#5B21B6',
      menuTextColor: '#EDE9FE',
      menuActiveBackgroundColor: '#7C3AED',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#6D28D9',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#DDD6FE',
      menuChevronColor: '#C4B5FD'
    }
  },
  {
    id: 'emerald',
    label: 'Emerald',
    caption: 'Fresh emerald sidebar with a sky-blue accent.',
    values: {
      themeMode: 'CUSTOM',
      cornerRadiusStyle: 'ROUNDED',
      themePrimaryColor: '#059669',
      themeSecondaryColor: '#0284C7',
      themeTertiaryColor: '#CA8A04',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#065F46',
      menuTextColor: '#D1FAE5',
      menuActiveBackgroundColor: '#059669',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#047857',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#A7F3D0',
      menuChevronColor: '#6EE7B7'
    }
  },
  {
    id: 'modern-light',
    label: 'Modern Light',
    caption: 'Clean white sidebar with circular icon badges.',
    values: {
      themeMode: 'LIGHT',
      cornerRadiusStyle: 'PILL',
      themePrimaryColor: null,
      themeSecondaryColor: null,
      themeTertiaryColor: null,
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: null,
      menuTextColor: null,
      menuActiveBackgroundColor: null,
      menuActiveTextColor: null,
      menuHoverBackgroundColor: null,
      menuHoverTextColor: null,
      menuIconColor: null,
      menuChevronColor: null
    }
  },
  {
    id: 'dark',
    label: 'Dark',
    caption: 'Full dark chrome, easy on the eyes.',
    values: {
      themeMode: 'DARK',
      cornerRadiusStyle: 'ROUNDED',
      themePrimaryColor: null,
      themeSecondaryColor: null,
      themeTertiaryColor: null,
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: null,
      menuTextColor: null,
      menuActiveBackgroundColor: null,
      menuActiveTextColor: null,
      menuHoverBackgroundColor: null,
      menuHoverTextColor: null,
      menuIconColor: null,
      menuChevronColor: null
    }
  }
];
