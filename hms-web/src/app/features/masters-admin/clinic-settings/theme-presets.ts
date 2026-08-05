import { ClinicSettings } from './clinic-settings.model';

export type ThemePresetId = 'healthcare-blue' | 'indigo' | 'teal' | 'purple' | 'emerald' | 'modern-light' | 'dark';

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
  | 'menuHoverIconColor'
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
 * (only the sidebar itself picks up the bold preset color). Every menu
 * text/background and active-text/active-background pair here is verified
 * >= 4.5:1 (WCAG AA for normal text) - see the Theme Studio redesign
 * proposal's contrast audit; a couple of the original active-state pairs
 * (Indigo/Teal/Emerald) were slightly too light to pass and were darkened.
 */
export const THEME_PRESETS: ThemePreset[] = [
  {
    id: 'healthcare-blue',
    label: 'Healthcare Blue',
    caption: "This app's own default palette - a calm, clinical blue.",
    values: {
      themeMode: 'CUSTOM',
      cornerRadiusStyle: 'ROUNDED',
      themePrimaryColor: '#0B5FA6',
      themeSecondaryColor: '#6A4FB0',
      themeTertiaryColor: '#0E8074',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#0B3B63',
      menuTextColor: '#CFE0F0',
      menuActiveBackgroundColor: '#0B5FA6',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#0A5490',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#9CC2E0',
      menuHoverIconColor: '#FFFFFF',
      menuChevronColor: '#7FAFD6'
    }
  },
  {
    id: 'indigo',
    label: 'Indigo',
    caption: 'Bold indigo sidebar with a teal accent.',
    values: {
      themeMode: 'CUSTOM',
      cornerRadiusStyle: 'ROUNDED',
      // Deeper jewel-tone accents (Tailwind 700/800 range) instead of the
      // original bright 500-range hues - those read as loud/candy-like
      // against the sidebar rather than the restrained, clinical look
      // Healthcare Blue set as the bar (see the redesign proposal's design
      // principles). Only the icon-dot/accent colors changed here - every
      // menu text/background pair below is untouched from the WCAG audit.
      themePrimaryColor: '#4338CA',
      themeSecondaryColor: '#4F46E5',
      themeTertiaryColor: '#0D9488',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#4338CA',
      menuTextColor: '#E0E7FF',
      menuActiveBackgroundColor: '#524BE8',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#4F46E5',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#C7D2FE',
      menuHoverIconColor: '#FFFFFF',
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
      themePrimaryColor: '#0F766E',
      themeSecondaryColor: '#0E7490',
      themeTertiaryColor: '#B45309',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#115E59',
      menuTextColor: '#CCFBF1',
      menuActiveBackgroundColor: '#0B7A70',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#0F766E',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#99F6E4',
      menuHoverIconColor: '#FFFFFF',
      menuChevronColor: '#5EEAD4'
    }
  },
  {
    id: 'purple',
    label: 'Purple',
    caption: 'Rich purple sidebar with a magenta accent.',
    values: {
      themeMode: 'CUSTOM',
      cornerRadiusStyle: 'ROUNDED',
      themePrimaryColor: '#6D28D9',
      themeSecondaryColor: '#7C3AED',
      themeTertiaryColor: '#A21CAF',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#5B21B6',
      menuTextColor: '#EDE9FE',
      menuActiveBackgroundColor: '#7C3AED',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#6D28D9',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#DDD6FE',
      menuHoverIconColor: '#FFFFFF',
      menuChevronColor: '#C4B5FD'
    }
  },
  {
    id: 'emerald',
    label: 'Emerald',
    caption: 'Fresh emerald sidebar with a deep sky-blue accent.',
    values: {
      themeMode: 'CUSTOM',
      cornerRadiusStyle: 'ROUNDED',
      themePrimaryColor: '#047857',
      themeSecondaryColor: '#0369A1',
      themeTertiaryColor: '#A16207',
      headerBackgroundColor: null,
      footerBackgroundColor: null,
      menuBackgroundColor: '#065F46',
      menuTextColor: '#D1FAE5',
      menuActiveBackgroundColor: '#047C54',
      menuActiveTextColor: '#FFFFFF',
      menuHoverBackgroundColor: '#047857',
      menuHoverTextColor: '#FFFFFF',
      menuIconColor: '#A7F3D0',
      menuHoverIconColor: '#FFFFFF',
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
      menuHoverIconColor: null,
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
      menuHoverIconColor: null,
      menuChevronColor: null
    }
  }
];
