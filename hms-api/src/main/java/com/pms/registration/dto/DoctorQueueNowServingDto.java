package com.pms.registration.dto;

import com.pms.registration.entity.DoctorQueuePriority;
import java.time.LocalDate;

/**
 * Public, unauthenticated view of a doctor's live queue for the Doctor Queue
 * display board - deliberately carries zero patient fields (no name, age,
 * gender, or reason), unlike DoctorQueueDashboardDto, since this is broadcast
 * to a waiting-room TV/monitor with no login in front of it.
 */
public record DoctorQueueNowServingDto(
        Long consultantId,
        String consultantName,
        String consultingRoomLabel,
        LocalDate queueDate,
        String currentTokenDisplay,
        DoctorQueuePriority currentPriority,
        long waitingCount,
        boolean moduleEnabled) {
}
