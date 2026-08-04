package com.pms.cashier.dto;

import com.pms.cashier.entity.PaymentRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequestDto(
        @NotNull Long admissionId, @NotNull PaymentRequestType requestType, @NotNull @Positive Double amount, String description) {
}
