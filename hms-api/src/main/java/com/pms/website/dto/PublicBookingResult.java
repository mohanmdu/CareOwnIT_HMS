package com.pms.website.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PublicBookingResult(
        Long appointmentId, String patientName, String consultantName, LocalDate date, LocalTime slotTime) {
}
