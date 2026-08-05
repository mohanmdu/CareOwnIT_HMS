import { chartThemeVersion } from '../../core/services/chart-theme.signal';

/**
 * Chart.js paints to <canvas> and can't live-resolve CSS custom properties,
 * so each quadrant reads these via getComputedStyle rather than referencing
 * the --hms-chart-series-* var() strings directly. Each function also reads
 * chartThemeVersion() first, purely to register a signal dependency - every
 * one of these is called from inside an Angular computed() in a quadrant
 * component (e.g. op-revenue-quadrant.component.ts's chartData), so reading
 * a signal here makes that computed() re-run (and this function re-read the
 * DOM with getComputedStyle) whenever ThemeService applies a theme change,
 * instead of only when the quadrant's own data signals change. Without
 * this, changing the theme while the CEO Dashboard is already open left the
 * chart showing stale colors until something unrelated (e.g. a date-range
 * change) happened to force a recompute.
 */
export function chartSeriesColors(): string[] {
  chartThemeVersion();
  const root = getComputedStyle(document.documentElement);
  return [1, 2, 3, 4, 5].map((n) => root.getPropertyValue(`--hms-chart-series-${n}`).trim());
}

export function chartTextColor(): string {
  chartThemeVersion();
  return getComputedStyle(document.documentElement).getPropertyValue('--hms-color-text').trim();
}

export function chartMutedColor(): string {
  chartThemeVersion();
  return getComputedStyle(document.documentElement).getPropertyValue('--hms-color-text-muted').trim();
}

export function chartBorderColor(): string {
  chartThemeVersion();
  return getComputedStyle(document.documentElement).getPropertyValue('--hms-color-border').trim();
}
