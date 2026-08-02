package com.pms.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClientAdminBootstrapRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "Mobile number must be exactly 10 digits") String mobileNumber,
        @NotBlank String username,
        @NotBlank String initialPassword) {
}
