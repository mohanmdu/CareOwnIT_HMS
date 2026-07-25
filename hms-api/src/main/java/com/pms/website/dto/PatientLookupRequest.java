package com.pms.website.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PatientLookupRequest(
        @NotBlank @Pattern(regexp = "\\d{10}", message = "Mobile number must be exactly 10 digits") String mobileNumber) {
}
