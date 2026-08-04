package com.pms.registration.controller;

import com.pms.registration.dto.PatientReportAuditLogRowDto;
import com.pms.registration.dto.PatientReportDeleteRequestDto;
import com.pms.registration.dto.PatientReportDto;
import com.pms.registration.dto.ShareLinkDto;
import com.pms.registration.service.PatientReportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/registration/patient-reports")
public class PatientReportController {

    private final PatientReportService service;

    public PatientReportController(PatientReportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientReportDto upload(
            @RequestParam Long patientId,
            @RequestParam(required = false) String comments,
            @RequestParam("file") MultipartFile file) {
        return service.upload(patientId, comments, file);
    }

    @GetMapping("/{id}")
    public PatientReportDto get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/share-link")
    public ShareLinkDto shareLink(@PathVariable Long id) {
        return service.createShareLink(id);
    }

    @GetMapping("/active")
    public List<PatientReportDto> active(@RequestParam Long patientId) {
        return service.getActiveFiles(patientId);
    }

    @GetMapping("/deleted")
    public List<PatientReportDto> deleted(@RequestParam Long patientId) {
        return service.getDeletedFiles(patientId);
    }

    @PatchMapping("/{id}/delete")
    public ResponseEntity<Void> softDelete(@PathVariable Long id, @Valid @RequestBody PatientReportDeleteRequestDto request) {
        service.softDelete(id, request.reason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
        service.permanentDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-log/patient")
    public List<PatientReportAuditLogRowDto> patientAuditLog() {
        return service.getPatientAuditLog();
    }

    @GetMapping("/audit-log/doctor")
    public List<PatientReportAuditLogRowDto> doctorAuditLog() {
        return service.getDoctorAuditLog();
    }
}
