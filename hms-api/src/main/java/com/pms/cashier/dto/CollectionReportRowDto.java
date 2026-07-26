package com.pms.cashier.dto;

import java.time.Instant;

/**
 * One unified row across all 5 real collection sources (Appointment, OP
 * Direct Billing, Lab/Investigation, IP Payment, Pharmacy Sale) - see
 * CollectionReportService for the per-source field mapping. departmentId/
 * consultantId are internal-only (precise in-memory filtering), not
 * necessarily rendered as-is on the frontend.
 */
public record CollectionReportRowDto(
        CollectionType collectionType,
        BillType billType,
        Long sourceId,
        String patientName,
        String patientUhid,
        Instant billedAt,
        String billNumber,
        String paymentMode,
        String consultantName,
        String departmentName,
        String billedByUsername,
        String billedByDisplayName,
        Long departmentId,
        Long consultantId,
        double invoiceAmount,
        double discountAmount,
        double doctorReferralAmount,
        double receiptAmount,
        double refundAmount) {
}
