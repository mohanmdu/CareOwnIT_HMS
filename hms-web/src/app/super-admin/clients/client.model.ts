import type { ModuleKey } from '../../layout/package-config';

export interface ClientRecord {
  id: number;
  name: string;
  code: string;
  status: 'ACTIVE' | 'SUSPENDED';
  /** Kebab-case ModuleKey.key() values - same wire format as Role.permittedModules. */
  licensedModules: ModuleKey[];
  createdAt: string;
}

export type ClientCreateInput = Pick<ClientRecord, 'name' | 'code'>;

export interface ClientAdminBootstrapInput {
  name: string;
  mobileNumber: string;
  username: string;
  initialPassword: string;
}

export interface ClientAdminBootstrapResult {
  userId: number;
  username: string;
  roleName: string;
}
