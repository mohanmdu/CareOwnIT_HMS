package com.pms.ipadmission.controller;

import com.pms.cashier.entity.PaymentRequestType;
import com.pms.cashier.service.IpPaymentRequestService;
import com.pms.ipadmission.dto.AdmissionDto;
import com.pms.ipadmission.dto.AdvancePaymentRequest;
import com.pms.ipadmission.dto.CancelAdmissionRequest;
import com.pms.ipadmission.dto.ChangeRoomRequest;
import com.pms.ipadmission.dto.FinalizeDischargeRequest;
import com.pms.ipadmission.dto.InitiateDischargeRequest;
import com.pms.ipadmission.service.AdmissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST equivalent of the legacy IPAction admission/advance/discharge family
 * (migration doc §4.2).
 */
@RestController
@RequestMapping("/api/ip/admissions")
public class AdmissionController {

    private final AdmissionService service;
    private final IpPaymentRequestService paymentRequestService;

    public AdmissionController(AdmissionService service, IpPaymentRequestService paymentRequestService) {
        this.service = service;
        this.paymentRequestService = paymentRequestService;
    }

    @GetMapping
    public List<AdmissionDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AdmissionDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdmissionDto admit(@Valid @RequestBody AdmissionDto dto) {
        return service.admit(dto);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AdmissionDto register(@Valid @RequestBody AdmissionDto dto) {
        return service.register(dto);
    }

    @PostMapping("/{id}/photo")
    public AdmissionDto uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.uploadPhoto(id, file);
    }

    @PatchMapping("/{id}/admit")
    public AdmissionDto admitRegistered(@PathVariable Long id, @Valid @RequestBody AdmissionDto dto) {
        AdmissionDto admitted = service.admitRegistered(id, dto);
        if (dto.advanceAmount() != null && dto.advanceAmount() > 0) {
            paymentRequestService.createPreApproved(
                    id,
                    PaymentRequestType.ADVANCE,
                    dto.advanceAmount(),
                    "Advance",
                    admitted.paymentType() != null ? admitted.paymentType().name() : null);
            admitted = service.findById(id);
        }
        return admitted;
    }

    @PutMapping("/{id}")
    public AdmissionDto updateIntake(@PathVariable Long id, @Valid @RequestBody AdmissionDto dto) {
        return service.updateIntake(id, dto);
    }

    @PatchMapping("/{id}/advance-payment")
    public AdmissionDto addAdvancePayment(@PathVariable Long id, @Valid @RequestBody AdvancePaymentRequest body) {
        AdmissionDto current = service.findById(id);
        paymentRequestService.createPreApproved(
                id,
                PaymentRequestType.ADVANCE,
                body.amount(),
                "Advance",
                current.paymentType() != null ? current.paymentType().name() : null);
        return service.findById(id);
    }

    @PatchMapping("/{id}/initiate-discharge")
    public AdmissionDto initiateDischarge(@PathVariable Long id, @Valid @RequestBody InitiateDischargeRequest body) {
        return service.initiateDischarge(id, body.dischargeDate(), body.dischargeType());
    }

    @PatchMapping("/{id}/finalize-discharge")
    public AdmissionDto finalizeDischarge(@PathVariable Long id, @Valid @RequestBody FinalizeDischargeRequest body) {
        return service.finalizeDischarge(id, body.totalBilled(), body.dischargeSummary());
    }

    @PatchMapping("/{id}/change-room")
    public AdmissionDto changeRoom(@PathVariable Long id, @Valid @RequestBody ChangeRoomRequest body) {
        return service.changeRoom(id, body.roomId(), body.changedAt());
    }

    @PatchMapping("/{id}/cancel")
    public AdmissionDto cancel(@PathVariable Long id, @Valid @RequestBody CancelAdmissionRequest body) {
        return service.cancelAdmission(id, body.reason());
    }
}
