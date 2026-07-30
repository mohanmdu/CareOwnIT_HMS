export interface Consultant {
  id: number | null;
  name: string;
  departmentId: number;
  departmentName: string | null;
  specializationId: number | null;
  specializationName: string | null;
  email: string | null;
  mobileNumber: string | null;
  consultationFee: number;
  profile: string | null;
  address: string | null;
  acceptingAppointments: boolean;
  imageUrl: string | null;
  queueTokenPrefix: string | null;
  consultingRoomLabel: string | null;
  active: boolean;
  publishedToWeb: boolean;
  createdAt: string | null;
  createdBy: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
  deactivatedAt: string | null;
  deactivatedBy: string | null;
}
