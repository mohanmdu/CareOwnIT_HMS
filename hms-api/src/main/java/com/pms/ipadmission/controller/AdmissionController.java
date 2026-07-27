package com.pms.ipadmission.controller;

import com.pms.cashier.entity.PaymentRequestType;
import com.pms.cashier.service.IpPaymentRequestService;
import com.pms.ipadmission.dto.AdmissionDto;
import com.pms.ipadmission.service.AdmissionService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    public AdmissionDto addAdvancePayment(@PathVariable Long id, @RequestBody Map<String, Double> body) {
        AdmissionDto current = service.findById(id);
        paymentRequestService.createPreApproved(
                id,
                PaymentRequestType.ADVANCE,
                body.getOrDefault("amount", 0.0),
                "Advance",
                current.paymentType() != null ? current.paymentType().name() : null);
        return service.findById(id);
    }

    @PatchMapping("/{id}/initiate-discharge")
    public AdmissionDto initiateDischarge(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object rawDate = body.get("dischargeDate");
        LocalDateTime dischargeDate = rawDate != null ? LocalDateTime.parse(rawDate.toString()) : null;
        String dischargeType = (String) body.get("dischargeType");
        return service.initiateDischarge(id, dischargeDate, dischargeType);
    }

    @PatchMapping("/{id}/finalize-discharge")
    public AdmissionDto finalizeDischarge(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        double totalBilled = ((Number) body.getOrDefault("totalBilled", 0)).doubleValue();
        String dischargeSummary = (String) body.get("dischargeSummary");
        return service.finalizeDischarge(id, totalBilled, dischargeSummary);
    }

    @PatchMapping("/{id}/change-room")
    public AdmissionDto changeRoom(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long roomId = ((Number) body.get("roomId")).longValue();
        Object rawDate = body.get("changedAt");
        LocalDateTime changedAt = rawDate != null ? LocalDateTime.parse(rawDate.toString()) : null;
        return service.changeRoom(id, roomId, changedAt);
    }

    @PatchMapping("/{id}/cancel")
    public AdmissionDto cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.cancelAdmission(id, body.get("reason"));
    }
}
