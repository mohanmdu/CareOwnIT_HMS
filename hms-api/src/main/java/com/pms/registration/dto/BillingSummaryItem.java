package com.pms.registration.dto;

import java.time.LocalDate;

/**
 * Unifies the app's 2 separate OP/IP billing paths (OpDirectBilling, IP
 * billing) into one shape for the Patient Information screen's Billing
 * Details/Payments Details/Cancelled Details tabs - neither of those 2
 * entities share a common DTO today, this is display-only, not a new billing
 * model.
 */
public record BillingSummaryItem(
        BillingSource source,
        Long id,
        String invoiceNumber,
        LocalDate billedDate,
        Double invoicedAmount,
        Double paidAmount,
        Double discountAmount,
        Double dueAmount,
        boolean cancelled,
        String cancellationReason,
        /** True only for Appointment/OpDirectBilling-sourced cancellations - IP refund/credit-note isn't built yet, see the Patient Information plan. */
        boolean refundEligible) {

    public enum BillingSource {
        OP_DIRECT_BILLING,
        IP_BILLING
    }
}
