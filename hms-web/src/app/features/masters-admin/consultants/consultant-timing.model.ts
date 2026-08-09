export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export const DAYS_OF_WEEK: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY'
];

export type Session = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'NIGHT';

export const SESSIONS: Session[] = ['MORNING', 'AFTERNOON', 'EVENING', 'NIGHT'];

export const SESSION_LABELS: Record<Session, string> = {
  MORNING: 'Morning Session',
  AFTERNOON: 'Afternoon Session',
  EVENING: 'Evening Session',
  NIGHT: 'Night Session'
};

/** Fixed time-of-day window each session is restricted to (mirrors the backend Session enum). No session wraps past midnight. */
export const SESSION_RANGES: Record<Session, { start: string; end: string }> = {
  MORNING: { start: '06:00', end: '11:45' },
  AFTERNOON: { start: '12:00', end: '17:45' },
  EVENING: { start: '18:00', end: '23:45' },
  NIGHT: { start: '00:00', end: '05:45' }
};

/** Default dropdown granularity - see generateTimeOptions. */
export const DEFAULT_TIME_OPTION_INTERVAL_MINUTES = 15;

function toMinutes(time: string): number {
  const [hours, minutes] = time.split(':').map(Number);
  return hours * 60 + minutes;
}

/**
 * Every "HH:mm" tick from `start` to `end` inclusive, `intervalMinutes` apart
 * (default 15) - reusable anywhere a dropdown-style time list needs to be
 * generated, not just here. Always zero-padded 24h "HH:mm".
 */
export function generateTimeOptions(
  start: string,
  end: string,
  intervalMinutes: number = DEFAULT_TIME_OPTION_INTERVAL_MINUTES
): string[] {
  const startMinutes = toMinutes(start);
  const endMinutes = toMinutes(end);
  const options: string[] = [];
  for (let minutes = startMinutes; minutes <= endMinutes; minutes += intervalMinutes) {
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    options.push(`${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}`);
  }
  return options;
}

export function isTimeWithinSession(session: Session, time: string): boolean {
  const range = SESSION_RANGES[session];
  return time >= range.start && time <= range.end;
}

/**
 * True if both times fall inside the session's allowed window and From is
 * strictly before To. Plain lexicographic "HH:mm" string comparison is safe
 * here: every value is always a zero-padded 24h "HH:mm" (either produced by
 * generateTimeOptions() or sliced from a backend LocalTime "HH:mm:ss"
 * string), and no session wraps past midnight, so lexicographic order always
 * matches chronological order.
 */
export function isValidSessionRange(session: Session, startTime: string, endTime: string): boolean {
  return isTimeWithinSession(session, startTime) && isTimeWithinSession(session, endTime) && startTime < endTime;
}

export interface ConsultantTiming {
  dayOfWeek: DayOfWeek;
  session: Session;
  startTime: string;
  endTime: string;
}

export interface ConsultantAvailability {
  slotsPerHour: number;
  timings: ConsultantTiming[];
}
