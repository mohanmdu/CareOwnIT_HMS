package com.pms.ipadmission.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record InitiateDischargeRequest(LocalDateTime dischargeDate, @NotBlank String dischargeType) {
}
