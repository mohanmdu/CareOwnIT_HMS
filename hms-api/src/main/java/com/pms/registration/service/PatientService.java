package com.pms.registration.service;

import com.pms.common.EntityNotFoundException;
import com.pms.common.FileStorageService;
import com.pms.registration.dto.PatientAuditLogDto;
import com.pms.registration.dto.PatientDto;
import com.pms.registration.entity.Patient;
import com.pms.registration.entity.PatientAuditLog;
import com.pms.registration.entity.PatientOriginModule;
import com.pms.registration.repository.PatientAuditLogRepository;
import com.pms.registration.repository.PatientRepository;
import java.time.Year;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Replaces AdminAction.patientRegistration (see migration doc §4.1). The
 * legacy NF-ID generation logic is not visible outside AdminDaoImpl; this
 * uses a simple year-prefixed sequential registration number instead -
 * revisit against the real numbering scheme before this touches production
 * data (see docs/appendix for the full legacy action catalog).
 *
 * Delete is soft (the `active` flag) so patients can be restored; a
 * separate permanent-delete operation does a real row delete. Every
 * register/update/delete/restore/permanent-delete is written to
 * PatientAuditLog for the Logs screen, attributed to the authenticated
 * HTTP Basic principal (see SecurityConfig) - this is the first module to
 * populate a "performed by" identity now that real auth carries one.
 */
@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository repository;
    private final PatientAuditLogRepository auditLogRepository;
    private final FileStorageService fileStorageService;

    public PatientService(
            PatientRepository repository, PatientAuditLogRepository auditLogRepository, FileStorageService fileStorageService) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<PatientDto> search(String query) {
        List<Patient> patients = (query == null || query.isBlank())
                ? repository.findByActiveTrueOrderByIdDesc()
                : repository.searchActive(query);
        return patients.stream().map(this::toDto).toList();
    }

    public List<PatientDto> findInactive() {
        return repository.findByActiveFalseOrderByUpdatedAtDesc().stream().map(this::toDto).toList();
    }

    public PatientDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    public List<PatientAuditLogDto> auditLogs() {
        return auditLogRepository.findAllByOrderByPerformedAtDesc().stream()
                .map(log -> new PatientAuditLogDto(
                        log.getId(), log.getOperation(), log.getPatientName(), log.getPerformedBy(), log.getPerformedAt()))
                .toList();
    }

    @Transactional
    public PatientDto register(PatientDto dto) {
        Patient patient = new Patient();
        patient.setRegistrationNumber(nextRegistrationNumber());
        patient.setActive(true);
        patient.setOriginModule(dto.originModule() != null ? dto.originModule() : PatientOriginModule.FRONT_OFFICE);
        applyFields(patient, dto);
        Patient saved = repository.save(patient);
        recordAudit("REGISTER", displayName(saved));
        return toDto(saved);
    }

    @Transactional
    public PatientDto update(Long id, PatientDto dto) {
        Patient patient = getOrThrow(id);
        applyFields(patient, dto);
        Patient saved = repository.save(patient);
        recordAudit("UPDATE", displayName(saved));
        return toDto(saved);
    }

    @Transactional
    public void softDelete(Long id) {
        Patient patient = getOrThrow(id);
        patient.setActive(false);
        repository.save(patient);
        recordAudit("DELETE", displayName(patient));
    }

    @Transactional
    public void restore(Long id) {
        Patient patient = getOrThrow(id);
        patient.setActive(true);
        repository.save(patient);
        recordAudit("RESTORE", displayName(patient));
    }

    @Transactional
    public PatientDto uploadPhoto(Long id, MultipartFile file) {
        Patient patient = getOrThrow(id);
        String photoPath = fileStorageService.store(file, "patients");
        patient.setPhotoPath(photoPath);
        return toDto(repository.save(patient));
    }

    @Transactional
    public void permanentDelete(Long id) {
        Patient patient = getOrThrow(id);
        String name = displayName(patient);
        repository.delete(patient);
        recordAudit("PERMANENT_DELETE", name);
    }

    /**
     * Derives the next number from the highest one already in the table
     * (scoped to the current year's prefix) rather than a row count or an
     * in-memory counter seeded at startup - those drift out of sync with
     * what's actually stored whenever a patient is permanently deleted,
     * causing this to reissue an already-used registration number.
     */
    private String nextRegistrationNumber() {
        String prefix = "NF-" + Year.now().getValue() + "-";
        int next = repository
                .findTopByRegistrationNumberStartingWithOrderByRegistrationNumberDesc(prefix)
                .map(p -> Integer.parseInt(p.getRegistrationNumber().substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%05d", next);
    }

    private void applyFields(Patient patient, PatientDto dto) {
        patient.setFirstName(dto.firstName());
        patient.setLastName(dto.lastName());
        patient.setDateOfBirth(dto.dateOfBirth());
        patient.setGender(dto.gender());
        patient.setAge(dto.age());
        patient.setMobileNumber(dto.mobileNumber());
        patient.setEmail(dto.email());
        patient.setAddress(dto.address());
    }

    private void recordAudit(String operation, String patientName) {
        auditLogRepository.save(new PatientAuditLog(operation, patientName, currentUsername()));
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private String displayName(Patient patient) {
        return (patient.getLastName() == null || patient.getLastName().isBlank())
                ? patient.getFirstName()
                : patient.getFirstName() + " " + patient.getLastName();
    }

    private Patient getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Patient not found: " + id));
    }

    private PatientDto toDto(Patient patient) {
        return new PatientDto(
                patient.getId(),
                patient.getRegistrationNumber(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getDateOfBirth(),
                patient.getGender(),
                patient.getAge(),
                patient.getMobileNumber(),
                patient.getEmail(),
                patient.getAddress(),
                patient.isActive(),
                patient.getOriginModule(),
                patient.getPhotoPath());
    }
}
