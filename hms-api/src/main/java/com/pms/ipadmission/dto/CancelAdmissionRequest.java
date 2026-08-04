package com.pms.ipadmission.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelAdmissionRequest(@NotBlank String reason) {
}
