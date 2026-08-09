package com.pms.masters.entity;

import java.time.LocalTime;

/**
 * One of a consultant's four working sessions in a day (see ConsultantTiming),
 * each with a fixed allowed time-of-day window. Every session is a same-day
 * range (Morning 06:00-11:45, Afternoon 12:00-17:45, Evening 18:00-23:45,
 * Night 00:00-05:45) - none wraps past midnight, so every comparison below
 * is a plain, non-wrapping start/end check.
 */
public enum Session {
    MORNING(LocalTime.of(6, 0), LocalTime.of(11, 45)),
    AFTERNOON(LocalTime.of(12, 0), LocalTime.of(17, 45)),
    EVENING(LocalTime.of(18, 0), LocalTime.of(23, 45)),
    NIGHT(LocalTime.of(0, 0), LocalTime.of(5, 45));

    private final LocalTime rangeStart;
    private final LocalTime rangeEnd;

    Session(LocalTime rangeStart, LocalTime rangeEnd) {
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    public LocalTime rangeStart() {
        return rangeStart;
    }

    public LocalTime rangeEnd() {
        return rangeEnd;
    }

    /**
     * Always false now that no session's range wraps past midnight. Kept as
     * a method (not removed) so ConsultantTimingService's existing
     * "(next day)" suffix on its validation error message keeps compiling -
     * it now naturally never appends that suffix, which is correct.
     */
    public boolean wrapsMidnight() {
        return false;
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(rangeStart) && !time.isAfter(rangeEnd);
    }

    /** True if both times fall in this session's window and start comes strictly before end. */
    public boolean isValidRange(LocalTime start, LocalTime end) {
        return contains(start) && contains(end) && start.isBefore(end);
    }
}
