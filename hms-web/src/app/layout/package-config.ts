import { environment } from '../../environments/environment';
import { NavGroup, NAV_GROUPS, routesForModule } from './nav-config';

/** One entry per NavGroup in nav-config.ts - keep these in sync. */
export type ModuleKey =
  | 'overview'
  | 'patient-registration'
  | 'appointments'
  | 'insurance'
  | 'lab'
  | 'upload-reports'
  | 'pharmacy'
  | 'icd-codes'
  | 'room-ward'
  | 'ip-admission'
  | 'cashier'
  | 'discharge-summary'
  | 'ip-billing-master'
  | 'website-cms'
  | 'masters'
  | 'administration'
  | 'ceo-dashboard';

export type PackageTier = 'BASIC' | 'STANDARD' | 'PREMIUM';

/**
 * Which modules a deployment's sidenav shows, per package tier. Tiers are
 * additive (STANDARD = everything in BASIC plus more, PREMIUM = everything
 * in STANDARD plus more) - a client upgrading their package should never
 * lose a module they already had.
 *
 * 'overview', 'masters', and 'administration' are deliberately in every
 * tier - a deployment needs at least basic user/role/clinic-settings
 * administration to function regardless of which clinical modules it's
 * licensed for.
 *
 * Static config, no admin UI: the active tier is set once per deployment via
 * environment.activePackage. Changing a client's package means a redeploy,
 * not a runtime settings toggle - see the "package config" note in the
 * unified-patient-identity discussion for why this was chosen over a
 * backend-driven admin screen (cheaper to build now, easy to graduate to
 * later without changing how NAV_GROUPS itself is structured).
 */
const ALWAYS_ON: ModuleKey[] = ['overview', 'masters', 'administration'];

export const PACKAGES: Record<PackageTier, ModuleKey[]> = {
  BASIC: [...ALWAYS_ON, 'patient-registration', 'appointments'],
  STANDARD: [...ALWAYS_ON, 'patient-registration', 'appointments', 'lab', 'upload-reports', 'pharmacy', 'icd-codes'],
  PREMIUM: [
    ...ALWAYS_ON,
    'patient-registration',
    'appointments',
    'lab',
    'upload-reports',
    'pharmacy',
    'icd-codes',
    'insurance',
    'room-ward',
    'ip-admission',
    'cashier',
    'discharge-summary',
    'ip-billing-master',
    'website-cms',
    'ceo-dashboard'
  ]
};

/** Exported for the Role permission picker (see roles/role-permission-picker), which must only offer checkboxes for modules the deployment is actually licensed for. */
export function activeModuleKeys(): ModuleKey[] {
  const tier = (environment as { activePackage?: PackageTier }).activePackage ?? 'PREMIUM';
  return PACKAGES[tier] ?? PACKAGES.PREMIUM;
}

/**
 * Sidenav groups for the deployment's active package, in nav-config.ts's
 * defined order. Most groups are all-or-nothing (filtered by their own
 * moduleKey), but a group whose items carry their own moduleKey override
 * (e.g. Overview, which mixes an always-on Dashboard with a PREMIUM-only
 * Cashier Module) is filtered per item instead, so the group still shows
 * with just its enabled items rather than being all-or-nothing.
 *
 * @param permittedModules the signed-in user's role-permitted modules. When
 * omitted, every package-tier module is treated as permitted (today's
 * behavior, unchanged) - callers that haven't wired up AuthService yet don't
 * need to change. When provided, the effective visible set is the
 * intersection of the deployment's package tier and the role's permissions,
 * so a role can never see a module the deployment isn't even licensed for,
 * and a license upgrade can never surface a module the role wasn't granted.
 * @param permittedRoutes the signed-in user's role-permitted individual
 * pages, layered on top of permittedModules (see Role.permittedRoutes on the
 * backend). A module with zero of its own routes present in this set is
 * treated as "never page-restricted" and stays fully open - only once at
 * least one of a module's routes appears here does that module's page list
 * get pruned to just the routes present. This keeps every role that predates
 * page-level permissions working unchanged with no data migration.
 */
export function getVisibleNavGroups(permittedModules?: Set<ModuleKey>, permittedRoutes?: Set<string>): NavGroup[] {
  const tierEnabled = new Set(activeModuleKeys());
  const enabled = permittedModules ? new Set([...tierEnabled].filter((key) => permittedModules.has(key))) : tierEnabled;
  return NAV_GROUPS.filter((group) => enabled.has(group.moduleKey))
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => {
        const key = item.moduleKey ?? group.moduleKey;
        if (!enabled.has(key)) {
          return false;
        }
        if (!permittedRoutes) {
          return true;
        }
        const modulePages = routesForModule(key);
        const pageLevelRestricted = modulePages.some((route) => permittedRoutes.has(route));
        return !pageLevelRestricted || permittedRoutes.has(item.route);
      })
    }))
    .filter((group) => group.items.length > 0);
}
