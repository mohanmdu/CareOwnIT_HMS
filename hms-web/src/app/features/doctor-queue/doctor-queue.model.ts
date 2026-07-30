export type QueueSource = 'APPOINTMENT' | 'WALK_IN';
export type QueuePriority = 'NORMAL' | 'EMERGENCY';

export type DoctorQueueStatus =
  | 'CHECKED_IN'
  | 'WAITING'
  | 'CALLED'
  | 'IN_CONSULTATION'
  | 'COMPLETED'
  | 'SKIPPED'
  | 'NO_SHOW'
  | 'CANCELLED';

/** The worklist's displayStatus is either the literal 'BOOKED' placeholder (virtual row, no queue entry yet) or one of the 8 real statuses. */
export type WorklistDisplayStatus = 'BOOKED' | DoctorQueueStatus;

export interface DoctorQueueWorklistEntry {
  queueEntryId: number | null;
  appointmentId: number | null;
  patientId: number;
  patientName: string;
  patientRegistrationNumber: string | null;
  patientAge: number | null;
  patientGender: string | null;
  consultantId: number;
  consultantName: string | null;
  displayStatus: WorklistDisplayStatus;
  source: QueueSource | null;
  scheduledSlotTime: string | null;
  tokenDisplay: string | null;
  checkedInAt: string | null;
  priority: QueuePriority | null;
  sortTimestamp: string;
}

export interface DoctorQueueEntry {
  id: number;
  consultantId: number;
  consultantName: string | null;
  patientId: number;
  patientRegistrationNumber: string | null;
  patientName: string;
  patientAge: number | null;
  patientGender: string | null;
  appointmentId: number | null;
  queueDate: string;
  tokenNumber: number;
  tokenDisplay: string;
  source: QueueSource;
  scheduledSlotTime: string | null;
  status: DoctorQueueStatus;
  priority: QueuePriority;
  priorityReason: string | null;
  checkedInAt: string;
  calledAt: string | null;
  recallCount: number;
  consultationStartedAt: string | null;
  completedAt: string | null;
  reason: string | null;
}

export interface DoctorQueueDashboard {
  consultantId: number;
  consultantName: string | null;
  queueDate: string;
  currentCalled: DoctorQueueEntry | null;
  waiting: DoctorQueueEntry[];
  waitingCount: number;
  calledCount: number;
  completedCount: number;
  skippedCount: number;
  noShowCount: number;
  cancelledCount: number;
  totalToday: number;
}

export interface DoctorQueueAuditLog {
  id: number;
  operation: string;
  patientName: string | null;
  consultantName: string | null;
  queueDate: string | null;
  tokenDisplay: string | null;
  previousValue: string | null;
  newValue: string | null;
  performedBy: string | null;
  performedAt: string;
}

export interface WalkInRequest {
  patientId: number;
  consultantId: number;
  priority?: QueuePriority;
  priorityReason?: string;
}

/** PHI-free - backs the unauthenticated Doctor Queue display board. No patient fields, unlike DoctorQueueEntry. */
export interface DoctorQueueNowServing {
  consultantId: number;
  consultantName: string | null;
  consultingRoomLabel: string | null;
  queueDate: string;
  currentTokenDisplay: string | null;
  currentPriority: QueuePriority | null;
  waitingCount: number;
  moduleEnabled: boolean;
}
