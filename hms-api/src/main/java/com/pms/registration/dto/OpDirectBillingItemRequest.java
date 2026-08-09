package com.pms.registration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OpDirectBillingItemRequest(
        @NotNull Long componentId,
        @NotNull @Positive Integer quantity,
        @NotNull @Positive Double amount,
        String remarks,
        Long consultantId) {
}
