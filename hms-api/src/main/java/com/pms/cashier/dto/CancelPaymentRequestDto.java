package com.pms.cashier.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelPaymentRequestDto(@NotBlank String reason) {
}
