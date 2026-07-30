package com.pms.masters.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record ConsultantDto(
        Long id,
        @NotBlank String name,
        @NotNull Long departmentId,
        String departmentName,
        Long specializationId,
        String specializationName,
        String email,
        // optional (unlike Patient/Supplier/GeneralUser's required mobileNumber) - @Pattern
        // alone (no @NotBlank) passes null/omitted values per Bean Validation semantics.
        @Pattern(regexp = "\\d{10}", message = "Mobile number must be exactly 10 digits") String mobileNumber,
        @PositiveOrZero Double consultationFee,
        String profile,
        String address,
        Boolean acceptingAppointments,
        String imageUrl,
        String queueTokenPrefix,
        String consultingRoomLabel,
        Boolean active,
        Boolean publishedToWeb,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Instant deactivatedAt,
        String deactivatedBy) {
}
