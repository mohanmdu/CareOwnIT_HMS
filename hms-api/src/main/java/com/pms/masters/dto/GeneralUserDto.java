package com.pms.masters.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

// initialPassword is write-only - only ever read on create(), never
// populated back by GeneralUserService.toDto() (a response never carries a
// password or its hash). username is read/write, safe to return.
public record GeneralUserDto(
        Long id,
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "Mobile number must be exactly 10 digits") String mobileNumber,
        String email,
        @NotNull Long roleId,
        String roleName,
        Boolean active,
        @NotBlank String username,
        String initialPassword,
        Boolean mustChangePassword,
        Instant createdAt,
        String createdBy,
        Instant deactivatedAt,
        String deactivatedBy) {
}
