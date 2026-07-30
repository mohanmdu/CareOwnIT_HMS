package com.pms.registration.dto;

import com.pms.registration.entity.DoctorQueuePriority;
import com.pms.registration.entity.DoctorQueueSource;
import com.pms.registration.entity.DoctorQueueStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record DoctorQueueEntryDto(
        Long id,
        Long consultantId,
        String consultantName,
        Long patientId,
        String patientRegistrationNumber,
        String patientName,
        Integer patientAge,
        String patientGender,
        Long appointmentId,
        LocalDate queueDate,
        int tokenNumber,
        String tokenDisplay,
        DoctorQueueSource source,
        LocalTime scheduledSlotTime,
        DoctorQueueStatus status,
        DoctorQueuePriority priority,
        String priorityReason,
        Instant checkedInAt,
        Instant calledAt,
        int recallCount,
        Instant consultationStartedAt,
        Instant completedAt,
        String reason) {
}
