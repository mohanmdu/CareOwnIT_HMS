package com.pms.registration.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * The 8 real, persisted states of a DoctorQueueEntry. "BOOKED" is
 * deliberately NOT a value here - it's a virtual/display-only status for an
 * Appointment with no queue entry yet (see DoctorQueueService's Reception
 * worklist merge) and must never be persisted as a pseudo "row doesn't exist
 * yet" state.
 */
public enum DoctorQueueStatus {
    CHECKED_IN,
    WAITING,
    CALLED,
    IN_CONSULTATION,
    COMPLETED,
    SKIPPED,
    NO_SHOW,
    CANCELLED;

    private static final Set<DoctorQueueStatus> ACTIVE = EnumSet.of(CHECKED_IN, WAITING, CALLED, IN_CONSULTATION);

    /** True for the states an appointment-cancel cascade or a manual cancel should still touch. */
    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
