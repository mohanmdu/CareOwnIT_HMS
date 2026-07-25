export interface GeneralUser {
  id: number | null;
  name: string;
  mobileNumber: string;
  email: string | null;
  roleId: number;
  roleName: string | null;
  active: boolean;
  username: string;
  mustChangePassword: boolean;
  createdAt: string | null;
  createdBy: string | null;
  deactivatedAt: string | null;
  deactivatedBy: string | null;
}

export interface GeneralUserAuditLogEntry {
  id: number;
  operation: string;
  userName: string;
  performedBy: string;
  performedAt: string;
}
