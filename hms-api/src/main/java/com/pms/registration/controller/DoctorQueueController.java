package com.pms.registration.controller;

import com.pms.registration.dto.CheckInRequest;
import com.pms.registration.dto.DoctorQueueAuditLogDto;
import com.pms.registration.dto.DoctorQueueDashboardDto;
import com.pms.registration.dto.DoctorQueueEntryDto;
import com.pms.registration.dto.EscalatePriorityRequest;
import com.pms.registration.dto.NoShowForAppointmentRequest;
import com.pms.registration.dto.NoShowRequest;
import com.pms.registration.dto.QueueCancelRequest;
import com.pms.registration.dto.ReceptionWorklistEntryDto;
import com.pms.registration.dto.WalkInRegistrationRequest;
import com.pms.registration.service.DoctorQueueService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Backs both the Reception Check-In/Worklist screen and the Doctor
 * Dashboard. Every mutation reuses the existing plain IllegalArgumentException
 * (400) / EntityNotFoundException (404) handlers already registered in
 * GlobalExceptionHandler - no new exception types needed for this module.
 */
@RestController
@RequestMapping("/api/registration/doctor-queue")
public class DoctorQueueController {

    private final DoctorQueueService service;

    public DoctorQueueController(DoctorQueueService service) {
        this.service = service;
    }

    @GetMapping("/worklist")
    public List<ReceptionWorklistEntryDto> worklist(
            @RequestParam Long consultantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.receptionWorklist(consultantId, date);
    }

    @GetMapping("/dashboard")
    public DoctorQueueDashboardDto dashboard(
            @RequestParam Long consultantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.dashboard(consultantId, date);
    }

    @GetMapping("/{id}")
    public DoctorQueueEntryDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/audit-logs")
    public List<DoctorQueueAuditLogDto> auditLogs() {
        return service.auditLogs();
    }

    @GetMapping("/{id}/audit-logs")
    public List<DoctorQueueAuditLogDto> auditLogsForEntry(@PathVariable Long id) {
        return service.auditLogsForEntry(id);
    }

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorQueueEntryDto checkIn(@Valid @RequestBody CheckInRequest request) {
        return service.checkIn(request);
    }

    @PostMapping("/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorQueueEntryDto registerWalkIn(@Valid @RequestBody WalkInRegistrationRequest request) {
        return service.registerWalkIn(request);
    }

    @PatchMapping("/next-patient")
    public DoctorQueueEntryDto nextPatient(
            @RequestParam Long consultantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.nextPatient(consultantId, date);
    }

    @PatchMapping("/{id}/recall")
    public DoctorQueueEntryDto recall(@PathVariable Long id) {
        return service.recall(id);
    }

    @PatchMapping("/{id}/skip")
    public DoctorQueueEntryDto skip(@PathVariable Long id) {
        return service.skip(id);
    }

    @PatchMapping("/{id}/complete")
    public DoctorQueueEntryDto complete(@PathVariable Long id) {
        return service.complete(id);
    }

    @PatchMapping("/{id}/escalate")
    public DoctorQueueEntryDto escalate(@PathVariable Long id, @RequestBody(required = false) EscalatePriorityRequest request) {
        return service.escalate(id, request != null ? request.reason() : null);
    }

    @PatchMapping("/{id}/de-escalate")
    public DoctorQueueEntryDto deEscalate(@PathVariable Long id) {
        return service.deEscalate(id);
    }

    @PatchMapping("/{id}/cancel")
    public DoctorQueueEntryDto cancel(@PathVariable Long id, @Valid @RequestBody QueueCancelRequest request) {
        return service.cancel(id, request.reason());
    }

    @PatchMapping("/{id}/move-to-waiting")
    public DoctorQueueEntryDto moveToWaiting(@PathVariable Long id) {
        return service.moveBackToWaiting(id);
    }

    @PatchMapping("/{id}/no-show")
    public DoctorQueueEntryDto noShow(@PathVariable Long id, @RequestBody(required = false) NoShowRequest request) {
        return service.markNoShow(id, request != null ? request.reason() : null);
    }

    @PatchMapping("/no-show-for-appointment")
    public DoctorQueueEntryDto noShowForAppointment(@Valid @RequestBody NoShowForAppointmentRequest request) {
        return service.markNoShowForNeverCheckedIn(request);
    }
}
