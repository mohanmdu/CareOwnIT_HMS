import type { ModuleKey } from '../../../layout/package-config';

export interface Role {
  id: number | null;
  name: string;
  active: boolean;
  permittedModules: ModuleKey[];
  /** Individual sidenav pages permitted within an already-granted module - see role-list.component.ts and Role.permittedRoutes on the backend. */
  permittedRoutes: string[];
  /** Where a user with this role lands right after login, instead of '/dashboard'. Null means use the default. */
  defaultRoute: string | null;
}
