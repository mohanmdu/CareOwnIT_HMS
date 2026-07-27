package com.pms.registration.dto;

import com.pms.registration.entity.PaymentMode;
import java.time.Instant;

/**
 * Lightweight row for the Appointments screen's "Direct Billing" tab - no
 * line items (see OpDirectBillingReceiptDto for the full reprint shape,
 * fetched separately only when a row's receipt is actually opened).
 */
public record OpDirectBillingListEntryDto(
        Long id,
        Long invoiceNumber,
        String patientName,
        String patientRegistrationNumber,
        Double totalAmount,
        PaymentMode paymentMode,
        String billedBy,
        Instant billedAt,
        Double refundAmount,
        Long consultantId,
        String consultantName) {
}
