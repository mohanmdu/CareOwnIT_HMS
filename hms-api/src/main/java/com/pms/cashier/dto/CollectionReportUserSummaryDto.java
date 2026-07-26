package com.pms.cashier.dto;

public record CollectionReportUserSummaryDto(
        String username,
        String displayName,
        long transactionCount,
        double invoiceAmount,
        double discountAmount,
        double receiptAmount,
        double refundAmount,
        double netCollectionAmount) {
}
