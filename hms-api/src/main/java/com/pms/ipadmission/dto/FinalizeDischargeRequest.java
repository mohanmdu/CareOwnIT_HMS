package com.pms.ipadmission.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record FinalizeDischargeRequest(@NotNull @PositiveOrZero Double totalBilled, String dischargeSummary) {
}
