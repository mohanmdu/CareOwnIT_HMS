package com.pms.registration.dto;

import jakarta.validation.constraints.NotBlank;

public record QueueCancelRequest(@NotBlank String reason) {
}
