/**
 * Literal hex mirror of the light-theme color tokens in styles/_tokens.scss,
 * for *-print-styles.ts popup windows. A print popup (window.open + document.write)
 * is a brand-new document with none of the app's CSS custom properties available,
 * and print has no dark-mode concept, so this only mirrors the light branch -
 * import from here instead of re-hardcoding hex values in each print-styles file.
 */
export const PRINT_TOKENS = {
  text: '#1b1f24',
  textMuted: '#5b6673',
  border: '#dde3ea',
  borderStrong: '#c2cbd6',
  surfaceAlt: '#f6f8fa',
} as const;

/**
 * Print-safe table header emphasis: bold text + a heavier bottom border
 * instead of a background fill. Most browsers don't print background colors
 * unless the user opts in ("print backgrounds"), so a solid-fill header -
 * especially one paired with white text - can silently render blank, or
 * illegible, on real paper. Interpolate into each screen's own header
 * selector (e.g. `.bill-items-table th`) rather than assuming a bare `th`.
 */
export const PRINT_TABLE_HEADER_CSS = `
    font-weight: 700;
    border-bottom: 2px solid ${PRINT_TOKENS.borderStrong};
  `;
