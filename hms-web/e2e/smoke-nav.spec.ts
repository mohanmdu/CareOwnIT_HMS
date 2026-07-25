import { expect, test } from './fixtures';
import { NAV_GROUPS } from '../src/app/layout/nav-config';

/**
 * Tier 1: automatic, whole-app smoke coverage. Generated directly from
 * NAV_GROUPS (the same data the app shell renders its sidenav from), so
 * every route stays covered with zero extra maintenance as new nav items
 * are added - this file needs no changes when the app grows.
 */
for (const group of NAV_GROUPS) {
  test.describe(`${group.label}`, () => {
    for (const item of group.items) {
      test(`${item.label} loads without errors`, async ({ page }) => {
        const errors: string[] = [];
        page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));
        page.on('console', (msg) => {
          if (msg.type() === 'error') {
            errors.push(`console.error: ${msg.text()}`);
          }
        });

        await page.goto(item.route);
        await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

        expect(errors, `Errors on ${item.route}:\n${errors.join('\n')}`).toEqual([]);
      });
    }
  });
}
