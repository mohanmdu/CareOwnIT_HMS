package com.pms.registration.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PrescriptionWorklistEntryDto(
        Long appointmentId,
        Long directBillingId,
        String source,
        Long patientId,
        String patientName,
        String patientRegistrationNumber,
        Integer patientAge,
        String patientGender,
        String mobileNumber,
        String departmentName,
        String consultantName,
        LocalDate appointmentDate,
        LocalTime slotTime,
        boolean hasCaseSheet,
        LocalDate reviewDate) {
}
