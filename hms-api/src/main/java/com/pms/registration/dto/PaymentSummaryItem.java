package com.pms.registration.dto;

import java.time.Instant;

/**
 * For IP billing, one row per real IpPayment transaction. OP has no
 * separate payment-transaction log today (Invoice/OpDirectBilling just carry
 * a single paidAmount field), so each OP billing row becomes one synthetic
 * payment entry here - a stated assumption (see the Patient Information
 * plan), not a new OP payments ledger.
 */
public record PaymentSummaryItem(
        BillingSummaryItem.BillingSource source,
        Long billingId,
        String receiptNumber,
        Instant paidAt,
        Double amount,
        String paymentMode) {
}
