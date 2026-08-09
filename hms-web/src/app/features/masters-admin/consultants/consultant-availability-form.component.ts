import { Component, computed, input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { TimeSelectComponent } from '../../../shared/ui/time-select/time-select.component';
import {
  ConsultantAvailability,
  ConsultantTiming,
  DayOfWeek,
  DAYS_OF_WEEK,
  DEFAULT_TIME_OPTION_INTERVAL_MINUTES,
  generateTimeOptions,
  isValidSessionRange,
  Session,
  SESSION_LABELS,
  SESSION_RANGES,
  SESSIONS
} from './consultant-timing.model';

type TimeField = { startTime: string; endTime: string };
type Rows = Record<DayOfWeek, Record<Session, TimeField>>;

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday'
};

/**
 * Doctor Availability editor - 7 days x 4 sessions (morning/afternoon/evening/night),
 * each session's from/to left blank meaning "not available that session" -
 * plus a slots-per-hour setting. Embedded both in the standalone "Update
 * Timings" dialog and inline on the Add/Edit Consultant form (shown when
 * Appointment Status is Yes) so the markup isn't duplicated.
 *
 * Laid out as one mat-tab per session (not a single 7x4 table) - a flat
 * table needs 8 dropdown columns plus a day column, which cannot fit a
 * typical dialog width without horizontal scroll. Each tab instead shows
 * only that session's 7-day list, so at most 2 dropdowns wide is ever on
 * screen at once. A tab whose week has an invalid day gets a small dot next
 * to its label (sessionHasInvalid) so a problem on a currently-hidden tab
 * isn't invisible - isValid/cellInvalid themselves already check every
 * day/session regardless of which tab is active.
 *
 * Each session is restricted to a fixed time-of-day window (SESSION_RANGES).
 * From/To are `<app-time-select>` dropdowns populated from that window at a
 * configurable interval (see timeOptionIntervalMinutes) rather than a native
 * `<input type="time">`, so an admin can only ever pick an in-window,
 * on-the-grid value; isValidSessionRange still double-checks on submit that
 * From comes before To.
 */
@Component({
  selector: 'app-consultant-availability-form',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatSelectModule, MatTabsModule, TimeSelectComponent],
  templateUrl: './consultant-availability-form.component.html',
  styleUrl: './consultant-availability-form.component.scss'
})
export class ConsultantAvailabilityFormComponent implements OnInit {
  initialAvailability = input<ConsultantAvailability | null>(null);

  /**
   * UI-only granularity for the From/To dropdown option lists (e.g. 15 =
   * quarter-hour ticks). Purely how densely populated each `<app-time-select>`'s
   * list is - NOT persisted, NOT part of ConsultantAvailability, and NOT the
   * same concept as slotsPerHour below (how many bookable PATIENT appointment
   * slots the backend carves out of an hour - see AppointmentAvailabilityService).
   */
  timeOptionIntervalMinutes = input(DEFAULT_TIME_OPTION_INTERVAL_MINUTES);

  readonly days = DAYS_OF_WEEK;
  readonly sessions = SESSIONS;
  readonly sessionLabels = SESSION_LABELS;
  readonly sessionRanges = SESSION_RANGES;
  readonly dayLabels = DAY_LABELS;
  readonly slotsPerHourOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  slotsPerHour = 1;
  rows: Rows = this.buildEmptyRows();

  /**
   * One cached "HH:mm" option array per session (4 entries, not 56) - shared
   * by every From and every To dropdown for that session across all 7 days.
   * Only recomputes if timeOptionIntervalMinutes actually changes, never on
   * the 56 individual cell edits below.
   */
  private readonly sessionTimeOptions = computed<Record<Session, string[]>>(() => {
    const interval = this.timeOptionIntervalMinutes();
    const result = {} as Record<Session, string[]>;
    for (const session of SESSIONS) {
      const range = SESSION_RANGES[session];
      result[session] = generateTimeOptions(range.start, range.end, interval);
    }
    return result;
  });

  timeOptionsFor(session: Session): string[] {
    return this.sessionTimeOptions()[session];
  }

  ngOnInit(): void {
    const initial = this.initialAvailability();
    if (!initial) {
      return;
    }
    this.slotsPerHour = initial.slotsPerHour;
    for (const timing of initial.timings) {
      this.rows[timing.dayOfWeek][timing.session] = {
        startTime: timing.startTime.slice(0, 5),
        endTime: timing.endTime.slice(0, 5)
      };
    }
  }

  /** Drives the red highlight on the specific From/To cell that's wrong, rather than just a page-level warning. */
  cellInvalid(day: DayOfWeek, session: Session): boolean {
    const field = this.rows[day][session];
    if (Boolean(field.startTime) !== Boolean(field.endTime)) {
      return true;
    }
    return Boolean(field.startTime) && Boolean(field.endTime) && !isValidSessionRange(session, field.startTime, field.endTime);
  }

  /** Drives the small dot on a session's tab label - lets an admin notice a problem on a day that's on a currently-hidden tab. */
  sessionHasInvalid(session: Session): boolean {
    return this.days.some((day) => this.cellInvalid(day, session));
  }

  get isValid(): boolean {
    for (const day of this.days) {
      for (const session of this.sessions) {
        if (this.cellInvalid(day, session)) {
          return false;
        }
      }
    }
    return true;
  }

  getValue(): ConsultantAvailability {
    const timings: ConsultantTiming[] = [];
    for (const day of this.days) {
      for (const session of this.sessions) {
        const field = this.rows[day][session];
        if (field.startTime && field.endTime) {
          timings.push({ dayOfWeek: day, session, startTime: field.startTime, endTime: field.endTime });
        }
      }
    }
    return { slotsPerHour: this.slotsPerHour, timings };
  }

  private buildEmptyRows(): Rows {
    const rows = {} as Rows;
    for (const day of DAYS_OF_WEEK) {
      rows[day] = {} as Record<Session, TimeField>;
      for (const session of SESSIONS) {
        rows[day][session] = { startTime: '', endTime: '' };
      }
    }
    return rows;
  }
}
