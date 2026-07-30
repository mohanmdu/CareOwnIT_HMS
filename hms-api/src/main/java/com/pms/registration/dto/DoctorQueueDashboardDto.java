package com.pms.registration.dto;

import java.time.LocalDate;
import java.util.List;

public record DoctorQueueDashboardDto(
        Long consultantId,
        String consultantName,
        LocalDate queueDate,
        DoctorQueueEntryDto currentCalled,
        List<DoctorQueueEntryDto> waiting,
        long waitingCount,
        long calledCount,
        long completedCount,
        long skippedCount,
        long noShowCount,
        long cancelledCount,
        long totalToday) {
}
