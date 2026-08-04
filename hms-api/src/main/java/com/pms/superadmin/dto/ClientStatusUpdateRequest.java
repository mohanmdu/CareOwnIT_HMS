package com.pms.superadmin.dto;

import com.pms.tenant.entity.ClientStatus;
import jakarta.validation.constraints.NotNull;

public record ClientStatusUpdateRequest(@NotNull ClientStatus status) {
}
