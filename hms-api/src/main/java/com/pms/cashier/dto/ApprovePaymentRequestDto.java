package com.pms.cashier.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovePaymentRequestDto(@NotBlank String paymentMode) {
}
