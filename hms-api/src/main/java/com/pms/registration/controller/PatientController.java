package com.pms.registration.controller;

import com.pms.registration.dto.PatientAuditLogDto;
import com.pms.registration.dto.PatientDto;
import com.pms.registration.dto.PatientSummaryDto;
import com.pms.registration.service.PatientService;
import com.pms.registration.service.PatientSummaryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST equivalent of the legacy patientRegistration Struts action
 * (migration doc §4.1), extended with soft delete/restore/permanent delete
 * and an audit log feed for the Patient Registration screen.
 */
@RestController
@RequestMapping("/api/registration/patients")
public class PatientController {

    private final PatientService service;
    private final PatientSummaryService summaryService;

    public PatientController(PatientService service, PatientSummaryService summaryService) {
        this.service = service;
        this.summaryService = summaryService;
    }

    @GetMapping
    public List<PatientDto> search(@RequestParam(required = false) String query) {
        return service.search(query);
    }

    @GetMapping("/{id}/summary")
    public PatientSummaryDto summary(@PathVariable Long id) {
        return summaryService.summary(id);
    }

    @GetMapping("/inactive")
    public List<PatientDto> inactive() {
        return service.findInactive();
    }

    @GetMapping("/audit-logs")
    public List<PatientAuditLogDto> auditLogs() {
        return service.auditLogs();
    }

    @GetMapping("/{id}")
    public PatientDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientDto register(@Valid @RequestBody PatientDto dto) {
        return service.register(dto);
    }

    @PutMapping("/{id}")
    public PatientDto update(@PathVariable Long id, @Valid @RequestBody PatientDto dto) {
        return service.update(id, dto);
    }

    @PostMapping("/{id}/photo")
    public PatientDto uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.uploadPhoto(id, file);
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long id) {
        service.restore(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
        service.permanentDelete(id);
        return ResponseEntity.noContent().build();
    }
}
