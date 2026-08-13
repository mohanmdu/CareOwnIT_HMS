import type { ModuleKey } from '../../layout/package-config';

export interface ClientRecord {
  id: number;
  name: string;
  code: string;
  status: 'ACTIVE' | 'SUSPENDED';
  /** Kebab-case ModuleKey.key() values - same wire format as Role.permittedModules. */
  licensedModules: ModuleKey[];
  /** This client's public-website domain (e.g. clienta-hospital.com), or null if not yet configured - see hms-api's DomainTenantResolutionFilter. */
  domain: string | null;
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

export type ClientDomainStatus = 'NOT_SET' | 'LIVE' | 'UNREACHABLE';

export type ClientDatabaseStatus = 'PROVISIONING' | 'READY' | 'FAILED' | 'SUSPENDED';

export interface ClientDatabaseRecord {
  clientId: number;
  host: string;
  port: number;
  schemaName: string;
  status: ClientDatabaseStatus;
  schemaVersion: string | null;
}
