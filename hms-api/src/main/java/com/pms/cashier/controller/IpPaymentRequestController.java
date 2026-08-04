package com.pms.cashier.controller;

import com.pms.cashier.dto.AdvanceReportRowDto;
import com.pms.cashier.dto.ApprovePaymentRequestDto;
import com.pms.cashier.dto.CancelPaymentRequestDto;
import com.pms.cashier.dto.CancellationRequestRowDto;
import com.pms.cashier.dto.CreatePaymentRequestDto;
import com.pms.cashier.dto.IpPaymentRequestDto;
import com.pms.cashier.service.IpPaymentRequestService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Cashier Approval Workflow REST endpoints (PDF: Advance Request -> Cashier Dashboard -> IP Approval Queue -> Receipt). */
@RestController
@RequestMapping("/api/cashier/payment-requests")
public class IpPaymentRequestController {

    private final IpPaymentRequestService service;

    public IpPaymentRequestController(IpPaymentRequestService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<IpPaymentRequestDto> listPending() {
        return service.listPending();
    }

    @GetMapping("/pending/count")
    public Map<String, Long> countPending() {
        return Map.of("ip", service.countPending());
    }

    @GetMapping("/{id}")
    public IpPaymentRequestDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IpPaymentRequestDto create(@Valid @RequestBody CreatePaymentRequestDto body) {
        return service.create(body.admissionId(), body.requestType(), body.amount(), body.description());
    }

    @PatchMapping("/{id}/approve")
    public IpPaymentRequestDto approve(@PathVariable Long id, @Valid @RequestBody ApprovePaymentRequestDto body) {
        return service.approve(id, body.paymentMode());
    }

    @GetMapping("/advance-report")
    public List<AdvanceReportRowDto> advanceReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return service.getAdvanceReport(fromDate, toDate);
    }

    @GetMapping("/cancellable")
    public List<CancellationRequestRowDto> cancellable(@RequestParam String uhid) {
        return service.searchCancellableByUhid(uhid);
    }

    @PatchMapping("/{id}/cancel")
    public IpPaymentRequestDto cancel(@PathVariable Long id, @Valid @RequestBody CancelPaymentRequestDto body) {
        return service.cancel(id, body.reason());
    }
}
