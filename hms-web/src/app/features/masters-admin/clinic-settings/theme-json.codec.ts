import { THEME_MODE_OPTIONS } from './clinic-settings.model';
import { ThemeFormSnapshot } from './theme-history.service';

const SCHEMA_TAG = 'hms-theme/v1';
const HEX_PATTERN = /^#[0-9a-fA-F]{6}$/;

const VALID_THEME_MODES = new Set(THEME_MODE_OPTIONS.map((o) => o.value));

/** Every ThemeFormSnapshot key that must hold either '' or a #RRGGBB hex string. */
const COLOR_KEYS = [
  'themePrimaryColor',
  'themeSecondaryColor',
  'themeTertiaryColor',
  'brandTextColor',
  'headerBackgroundColor',
  'footerBackgroundColor',
  'menuBackgroundColor',
  'menuTextColor',
  'menuActiveBackgroundColor',
  'menuActiveTextColor',
  'menuHoverBackgroundColor',
  'menuHoverTextColor',
  'menuIconColor',
  'menuHoverIconColor',
  'menuChevronColor'
] as const satisfies readonly (keyof ThemeFormSnapshot)[];

/** Only the mode + palette - deliberately excludes cornerRadiusStyle/fontFamily/fontSizeScale/footerText, same "never clobber typography or copy" rule theme-presets.ts already documents for presets. A theme shared between hospitals is a color palette, not each hospital's independent shape/type/copy choices. */
export type ThemeJsonSnapshot = Pick<ThemeFormSnapshot, 'themeMode' | (typeof COLOR_KEYS)[number]>;

export type ThemeJsonParseResult = { ok: true; snapshot: ThemeJsonSnapshot } | { ok: false; error: string };

/**
 * Round-trips a color palette to/from a shareable JSON document - the
 * mechanism behind "Export JSON", "Import JSON" (file), and "Paste JSON"
 * (see the Theme Studio redesign proposal).
 *
 * parse() never throws and never partially applies a malformed document -
 * every key is validated (unknown top-level keys rejected, every color
 * hex-checked) before a single field of the caller's form is touched, so a
 * bad paste can only ever surface as an error message, never a broken or
 * half-applied theme.
 */
export function serializeTheme(snapshot: ThemeJsonSnapshot): string {
  const colors: Record<string, string | null> = {};
  for (const key of COLOR_KEYS) {
    colors[key] = snapshot[key] || null;
  }
  const document = {
    $schema: SCHEMA_TAG,
    themeMode: snapshot.themeMode,
    colors
  };
  return JSON.stringify(document, null, 2);
}

export function parseTheme(raw: string): ThemeJsonParseResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return { ok: false, error: "That's not valid JSON - check for a missing brace or quote." };
  }

  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    return { ok: false, error: 'Expected a JSON object at the top level.' };
  }
  const doc = parsed as Record<string, unknown>;

  const knownTopLevelKeys = new Set(['$schema', 'themeMode', 'colors']);
  const unknownKey = Object.keys(doc).find((key) => !knownTopLevelKeys.has(key));
  if (unknownKey) {
    return { ok: false, error: `Unrecognized field "${unknownKey}" - this doesn't look like an HMS theme export.` };
  }
  if (doc['$schema'] !== SCHEMA_TAG) {
    return { ok: false, error: `Missing or unrecognized "$schema" - expected "${SCHEMA_TAG}".` };
  }
  if (typeof doc['themeMode'] !== 'string' || !VALID_THEME_MODES.has(doc['themeMode'] as never)) {
    return { ok: false, error: `"themeMode" must be one of: ${[...VALID_THEME_MODES].join(', ')}.` };
  }
  if (typeof doc['colors'] !== 'object' || doc['colors'] === null || Array.isArray(doc['colors'])) {
    return { ok: false, error: '"colors" must be an object.' };
  }
  const colorsDoc = doc['colors'] as Record<string, unknown>;
  const unknownColorKey = Object.keys(colorsDoc).find((key) => !COLOR_KEYS.includes(key as (typeof COLOR_KEYS)[number]));
  if (unknownColorKey) {
    return { ok: false, error: `Unrecognized color "${unknownColorKey}".` };
  }

  const colors: Record<string, string> = {};
  for (const key of COLOR_KEYS) {
    const value = colorsDoc[key];
    if (value === null || value === undefined) {
      colors[key] = '';
      continue;
    }
    if (typeof value !== 'string' || !HEX_PATTERN.test(value)) {
      return { ok: false, error: `"colors.${key}" must be a 6-digit hex color (e.g. #0B5FA6) or null - got ${JSON.stringify(value)}.` };
    }
    colors[key] = value.toUpperCase();
  }

  return {
    ok: true,
    snapshot: {
      themeMode: doc['themeMode'] as ThemeFormSnapshot['themeMode'],
      themePrimaryColor: colors['themePrimaryColor'],
      themeSecondaryColor: colors['themeSecondaryColor'],
      themeTertiaryColor: colors['themeTertiaryColor'],
      brandTextColor: colors['brandTextColor'],
      headerBackgroundColor: colors['headerBackgroundColor'],
      footerBackgroundColor: colors['footerBackgroundColor'],
      menuBackgroundColor: colors['menuBackgroundColor'],
      menuTextColor: colors['menuTextColor'],
      menuActiveBackgroundColor: colors['menuActiveBackgroundColor'],
      menuActiveTextColor: colors['menuActiveTextColor'],
      menuHoverBackgroundColor: colors['menuHoverBackgroundColor'],
      menuHoverTextColor: colors['menuHoverTextColor'],
      menuIconColor: colors['menuIconColor'],
      menuHoverIconColor: colors['menuHoverIconColor'],
      menuChevronColor: colors['menuChevronColor']
    }
  };
}
