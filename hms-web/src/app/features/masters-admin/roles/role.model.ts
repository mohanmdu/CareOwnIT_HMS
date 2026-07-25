import type { ModuleKey } from '../../../layout/package-config';

export interface Role {
  id: number | null;
  name: string;
  active: boolean;
  permittedModules: ModuleKey[];
}
