package com.pms.insurance.controller;

import com.pms.insurance.dto.PreAuthorizationRequestAmendDto;
import com.pms.insurance.dto.PreAuthorizationRequestApproveDto;
import com.pms.insurance.dto.PreAuthorizationRequestCancelDto;
import com.pms.insurance.dto.PreAuthorizationRequestCreateDto;
import com.pms.insurance.dto.PreAuthorizationRequestDto;
import com.pms.insurance.dto.PreAuthorizationRequestRaiseDto;
import com.pms.insurance.dto.PreAuthorizationRequestRejectDto;
import com.pms.insurance.service.PreAuthorizationRequestService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insurance/pre-authorization-requests")
public class PreAuthorizationRequestController {

    private final PreAuthorizationRequestService service;

    public PreAuthorizationRequestController(PreAuthorizationRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<PreAuthorizationRequestDto> list() {
        return service.findAll();
    }

    @GetMapping("/pending")
    public List<PreAuthorizationRequestDto> pending() {
        return service.findPending();
    }

    @GetMapping("/by-admission/{admissionId}")
    public List<PreAuthorizationRequestDto> byAdmission(@PathVariable Long admissionId) {
        return service.findByAdmission(admissionId);
    }

    @GetMapping("/approved")
    public List<PreAuthorizationRequestDto> approved(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String insurerName,
            @RequestParam(required = false) String patientUhid) {
        return service.findApprovedReport(from, to, insurerName, patientUhid);
    }

    @GetMapping("/rejected")
    public List<PreAuthorizationRequestDto> rejected(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String insurerName,
            @RequestParam(required = false) String patientUhid) {
        return service.findRejectedReport(from, to, insurerName, patientUhid);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PreAuthorizationRequestDto create(@Valid @RequestBody PreAuthorizationRequestCreateDto dto) {
        return service.create(dto);
    }

    @PatchMapping("/{id}/raise")
    public PreAuthorizationRequestDto raise(@PathVariable Long id, @Valid @RequestBody PreAuthorizationRequestRaiseDto dto) {
        return service.raise(id, dto);
    }

    @PatchMapping("/{id}/approve")
    public PreAuthorizationRequestDto approve(@PathVariable Long id, @Valid @RequestBody PreAuthorizationRequestApproveDto dto) {
        return service.approve(id, dto);
    }

    @PatchMapping("/{id}/reject")
    public PreAuthorizationRequestDto reject(@PathVariable Long id, @Valid @RequestBody PreAuthorizationRequestRejectDto dto) {
        return service.reject(id, dto);
    }

    @PatchMapping("/{id}/amend")
    public PreAuthorizationRequestDto amend(@PathVariable Long id, @Valid @RequestBody PreAuthorizationRequestAmendDto dto) {
        return service.amend(id, dto);
    }

    @PatchMapping("/{id}/cancel")
    public PreAuthorizationRequestDto cancel(@PathVariable Long id, @Valid @RequestBody PreAuthorizationRequestCancelDto body) {
        return service.cancel(id, body.reason());
    }
}
