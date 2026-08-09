package com.pms.registration.controller;

import com.pms.registration.dto.OpCaseSheetDto;
import com.pms.registration.dto.OpCaseSheetSaveRequest;
import com.pms.registration.dto.PrescriptionWorklistEntryDto;
import com.pms.registration.dto.ReviewDateReportEntryDto;
import com.pms.registration.service.OpCaseSheetService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/**
 * Patient Prescription / OP Case Sheet endpoints (migration doc's Patient
 * Prescription module).
 */
@RestController
@RequestMapping("/api/registration/op-case-sheets")
public class OpCaseSheetController {

    private final OpCaseSheetService service;

    public OpCaseSheetController(OpCaseSheetService service) {
        this.service = service;
    }

    @GetMapping("/worklist")
    public List<PrescriptionWorklistEntryDto> worklist(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long consultantId,
            @RequestParam(required = false) String search) {
        return service.worklist(fromDate, toDate, consultantId, search);
    }

    @GetMapping("/by-appointment/{appointmentId}")
    public OpCaseSheetDto getByAppointment(@PathVariable Long appointmentId) {
        return service.getOrCreateShell(appointmentId);
    }

    @PutMapping("/by-appointment/{appointmentId}")
    public OpCaseSheetDto save(@PathVariable Long appointmentId, @Valid @RequestBody OpCaseSheetSaveRequest request) {
        return service.save(appointmentId, request);
    }

    @GetMapping("/by-direct-billing/{directBillingId}")
    public OpCaseSheetDto getByDirectBilling(@PathVariable Long directBillingId) {
        return service.getOrCreateShellForDirectBilling(directBillingId);
    }

    @PutMapping("/by-direct-billing/{directBillingId}")
    public OpCaseSheetDto saveForDirectBilling(
            @PathVariable Long directBillingId, @Valid @RequestBody OpCaseSheetSaveRequest request) {
        return service.saveForDirectBilling(directBillingId, request);
    }

    @GetMapping("/review-date-report")
    public List<ReviewDateReportEntryDto> reviewDateReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "true") boolean upcoming) {
        return service.reviewDateReport(fromDate, toDate, upcoming);
    }
}
