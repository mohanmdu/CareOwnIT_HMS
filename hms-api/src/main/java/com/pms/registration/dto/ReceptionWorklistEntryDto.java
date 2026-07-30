package com.pms.registration.dto;

import com.pms.registration.entity.DoctorQueuePriority;
import com.pms.registration.entity.DoctorQueueSource;
import java.time.Instant;
import java.time.LocalTime;

/**
 * One unified row in the Reception worklist - either a virtual "BOOKED"
 * appointment with no queue entry yet (queueEntryId=null, source=null,
 * tokenDisplay=null), or a real DoctorQueueEntry (queueEntryId set,
 * displayStatus mirrors DoctorQueueStatus.name()). displayStatus is a plain
 * String, not DoctorQueueStatus, precisely so the virtual "BOOKED" label
 * never has to be forced into the same Java type as the 8 real persisted
 * values.
 */
public record ReceptionWorklistEntryDto(
        Long queueEntryId,
        Long appointmentId,
        Long patientId,
        String patientName,
        String patientRegistrationNumber,
        Integer patientAge,
        String patientGender,
        Long consultantId,
        String consultantName,
        String displayStatus,
        DoctorQueueSource source,
        LocalTime scheduledSlotTime,
        String tokenDisplay,
        Instant checkedInAt,
        DoctorQueuePriority priority,
        Instant sortTimestamp) {
}
