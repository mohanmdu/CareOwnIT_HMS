package com.pms.registration.dto;

import jakarta.validation.constraints.NotNull;

public record NoShowForAppointmentRequest(@NotNull Long appointmentId, String reason) {
}
