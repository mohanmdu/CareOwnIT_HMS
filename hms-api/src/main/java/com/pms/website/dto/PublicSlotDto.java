package com.pms.website.dto;

import com.pms.masters.entity.Session;
import com.pms.registration.dto.SlotStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Trimmed for anonymous public callers - deliberately omits
 * AppointmentSlotDto's appointmentId/patientId/patientName, which would
 * otherwise leak who booked a given slot to any visitor browsing
 * availability.
 */
public record PublicSlotDto(LocalDate date, LocalTime time, Session session, SlotStatus status) {
}
