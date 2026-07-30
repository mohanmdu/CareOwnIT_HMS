package com.pms.registration.dto;

import com.pms.registration.entity.DoctorQueuePriority;
import jakarta.validation.constraints.NotNull;

public record WalkInRegistrationRequest(
        @NotNull Long patientId, @NotNull Long consultantId, DoctorQueuePriority priority, String priorityReason) {
}
