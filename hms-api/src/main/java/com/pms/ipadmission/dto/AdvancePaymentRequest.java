package com.pms.ipadmission.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdvancePaymentRequest(@NotNull @Positive Double amount) {
}
