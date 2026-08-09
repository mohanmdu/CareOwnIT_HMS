package com.pms.registration.dto;

public record OpDirectBillingItemDto(
        Long id,
        String categoryName,
        String componentName,
        Integer quantity,
        Double amount,
        String remarks,
        Long consultantId,
        String consultantName) {
}
