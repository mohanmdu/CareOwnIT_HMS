package com.pms.registration.dto;

import com.pms.masters.entity.Session;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * `date` is the slot's calendar date - since every session is a same-day
 * range (see Session), this always equals the date slots were queried for.
 */
public record AppointmentSlotDto(
        LocalDate date,
        LocalTime time,
        Session session,
        SlotStatus status,
        Long appointmentId,
        Long patientId,
        String patientName) {
}
