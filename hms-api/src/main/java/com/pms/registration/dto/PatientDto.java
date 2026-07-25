package com.pms.registration.dto;

import com.pms.registration.entity.PatientOriginModule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record PatientDto(
        Long id,
        String registrationNumber,
        @NotBlank String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String gender,
        @Min(1) @Max(100) Integer age,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "Mobile number must be exactly 10 digits") String mobileNumber,
        String email,
        String address,
        Boolean active,
        // Required on create (register() defaults to FRONT_OFFICE if omitted); ignored on update - see PatientService.
        PatientOriginModule originModule,
        // Read-only - set via the dedicated uploadPhoto() endpoint, never through register()/update().
        String photoPath) {
}
