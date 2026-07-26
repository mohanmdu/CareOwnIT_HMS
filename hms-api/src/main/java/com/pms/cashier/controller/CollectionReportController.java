package com.pms.cashier.controller;

import com.pms.cashier.dto.BillType;
import com.pms.cashier.dto.CollectionReportDto;
import com.pms.cashier.dto.CollectionType;
import com.pms.cashier.service.CollectionReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cashier Module's all-sources Collection Report. */
@RestController
@RequestMapping("/api/cashier/reports/collection")
public class CollectionReportController {

    private final CollectionReportService service;

    public CollectionReportController(CollectionReportService service) {
        this.service = service;
    }

    @GetMapping
    public CollectionReportDto search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BillType billType,
            @RequestParam(required = false) CollectionType collectionType,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long consultantId,
            @RequestParam(required = false) String username) {
        return service.search(from, to, billType, collectionType, paymentMode, departmentId, consultantId, username);
    }
}
