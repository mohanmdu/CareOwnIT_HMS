package com.pms.website.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Either existingPatientId is set (the visitor picked a match from
 * patient-lookup, or "is this you?"), or newPatient is set (no match, or
 * they chose "none of these") - PublicAppointmentService.book() enforces
 * exactly one is present. mobileNumber is required either way: for an
 * existing patient it's re-verified server-side against that patient's real
 * mobile number (see PublicAppointmentService), and for a new one it's what
 * gets registered.
 */
public record PublicBookingRequest(
        @NotBlank @Pattern(regexp = "\\d{10}", message = "Mobile number must be exactly 10 digits") String mobileNumber,
        Long existingPatientId,
        @Valid NewPatientDetails newPatient,
        @NotNull Long consultantId,
        @NotNull LocalDate date,
        @NotNull LocalTime slotTime) {

    public record NewPatientDetails(
            @NotBlank String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            Integer age,
            String email,
            String address) {
    }
}
