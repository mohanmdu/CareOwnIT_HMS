package com.pms.cashier.dto;

import java.util.List;

public record CollectionReportDto(
        List<CollectionReportRowDto> rows,
        CollectionReportTotalsDto totals,
        List<CollectionReportUserSummaryDto> userSummary) {
}
