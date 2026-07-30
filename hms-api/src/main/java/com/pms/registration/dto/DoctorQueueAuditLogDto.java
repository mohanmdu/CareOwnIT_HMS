package com.pms.registration.dto;

import java.time.Instant;
import java.time.LocalDate;

public record DoctorQueueAuditLogDto(
        Long id,
        String operation,
        String patientName,
        String consultantName,
        LocalDate queueDate,
        String tokenDisplay,
        String previousValue,
        String newValue,
        String performedBy,
        Instant performedAt) {
}
