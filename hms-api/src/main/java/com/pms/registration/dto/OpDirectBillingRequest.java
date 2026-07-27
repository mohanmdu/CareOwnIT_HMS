package com.pms.registration.dto;

import com.pms.registration.entity.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OpDirectBillingRequest(
        @NotNull Long patientId,
        Long consultantId,
        @NotEmpty @Valid List<OpDirectBillingItemRequest> items,
        @NotNull PaymentMode paymentMode,
        String remarks) {
}
