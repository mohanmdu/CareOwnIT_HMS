import { signal } from '@angular/core';

/**
 * Bumped by ThemeService.applyTheme() every time it writes theme CSS custom
 * properties to the document. Chart.js paints to <canvas> and can't
 * live-resolve CSS custom properties on its own (see ceo-dashboard's
 * chart-colors.ts), so those color-reader functions read this signal too -
 * an Angular computed() that calls one of them picks up a reactive
 * dependency on live theme changes "for free", without ThemeService (core)
 * needing to import anything from the ceo-dashboard feature. A plain
 * counter, not the theme values themselves - the readers still go straight
 * to getComputedStyle for the actual colors, this only signals "something
 * changed, go re-read them."
 */
export const chartThemeVersion = signal(0);

export function bumpChartThemeVersion(): void {
  chartThemeVersion.update((version) => version + 1);
}
