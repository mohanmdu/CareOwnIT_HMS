package com.pms.insurance.dto;

import jakarta.validation.constraints.NotBlank;

public record PreAuthorizationRequestCancelDto(@NotBlank String reason) {
}
