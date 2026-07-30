package com.pms.registration.dto;

import jakarta.validation.constraints.NotNull;

public record CheckInRequest(@NotNull Long appointmentId) {
}
