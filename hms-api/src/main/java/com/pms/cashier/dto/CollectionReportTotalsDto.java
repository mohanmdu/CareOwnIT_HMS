package com.pms.cashier.dto;

public record CollectionReportTotalsDto(
        double invoiceAmount,
        double discountAmount,
        double doctorReferralAmount,
        double receiptAmount,
        double refundAmount,
        double totalCollectionAmount) {
}
