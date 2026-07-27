package com.pms.registration.dto;

import com.pms.registration.entity.PaymentMode;
import java.time.Instant;
import java.util.List;

public record OpDirectBillingReceiptDto(
        Long id,
        Long invoiceNumber,
        String patientRegistrationNumber,
        String patientName,
        String gender,
        Integer age,
        String mobileNumber,
        Long consultantId,
        String consultantName,
        List<OpDirectBillingItemDto> items,
        Double totalAmount,
        PaymentMode paymentMode,
        String remarks,
        String billedBy,
        Instant billedAt,
        Double refundAmount) {
}
