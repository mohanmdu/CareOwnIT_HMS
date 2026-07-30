package com.pms.masters.service;

import com.pms.common.EntityNotFoundException;
import com.pms.common.FileStorageService;
import com.pms.masters.dto.ConsultantDto;
import com.pms.masters.entity.Consultant;
import com.pms.masters.entity.ConsultantAuditLog;
import com.pms.masters.entity.Department;
import com.pms.masters.entity.Specialization;
import com.pms.masters.repository.ConsultantAuditLogRepository;
import com.pms.masters.repository.ConsultantRepository;
import com.pms.masters.repository.DepartmentRepository;
import com.pms.masters.repository.SpecializationRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Create/update/deactivate/restore are written to ConsultantAuditLog
 * (mirroring DepartmentService) so the list screen can show who
 * created/last edited/deactivated each consultant.
 */
@Service
@Transactional(readOnly = true)
public class ConsultantService {

    private static final String CREATE = "CREATE";
    private static final String UPDATE = "UPDATE";
    private static final String DEACTIVATE = "DEACTIVATE";
    private static final String RESTORE = "RESTORE";

    private final ConsultantRepository repository;
    private final DepartmentRepository departmentRepository;
    private final SpecializationRepository specializationRepository;
    private final ConsultantAuditLogRepository auditLogRepository;
    private final FileStorageService fileStorageService;

    public ConsultantService(
            ConsultantRepository repository,
            DepartmentRepository departmentRepository,
            SpecializationRepository specializationRepository,
            ConsultantAuditLogRepository auditLogRepository,
            FileStorageService fileStorageService) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.specializationRepository = specializationRepository;
        this.auditLogRepository = auditLogRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<ConsultantDto> findActive() {
        return toDtos(repository.findByActiveTrueOrderByNameAsc());
    }

    public List<ConsultantDto> findInactive() {
        return toDtos(repository.findByActiveFalseOrderByUpdatedAtDesc());
    }

    public List<ConsultantDto> search(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return toDtos(repository.search(trimmed).stream().limit(20).toList());
    }

    public ConsultantDto findById(Long id) {
        return toDto(getOrThrow(id), latestByConsultant(CREATE), latestByConsultant(UPDATE), latestByConsultant(DEACTIVATE));
    }

    @Transactional
    public ConsultantDto create(ConsultantDto dto) {
        Consultant consultant = new Consultant();
        applyFields(consultant, dto);
        consultant.setActive(true);
        Consultant saved = repository.save(consultant);
        recordAudit(saved, CREATE);
        return findById(saved.getId());
    }

    @Transactional
    public ConsultantDto update(Long id, ConsultantDto dto) {
        Consultant consultant = getOrThrow(id);
        applyFields(consultant, dto);
        Consultant saved = repository.save(consultant);
        recordAudit(saved, UPDATE);
        return findById(saved.getId());
    }

    @Transactional
    public void deactivate(Long id) {
        Consultant consultant = getOrThrow(id);
        consultant.setActive(false);
        repository.save(consultant);
        recordAudit(consultant, DEACTIVATE);
    }

    @Transactional
    public void restore(Long id) {
        Consultant consultant = getOrThrow(id);
        consultant.setActive(true);
        repository.save(consultant);
        recordAudit(consultant, RESTORE);
    }

    @Transactional
    public void publish(Long id) {
        Consultant consultant = getOrThrow(id);
        consultant.setPublishedToWeb(true);
        repository.save(consultant);
    }

    @Transactional
    public void unpublish(Long id) {
        Consultant consultant = getOrThrow(id);
        consultant.setPublishedToWeb(false);
        repository.save(consultant);
    }

    @Transactional
    public ConsultantDto uploadImage(Long id, MultipartFile file) {
        Consultant consultant = getOrThrow(id);
        String imagePath = fileStorageService.store(file, "consultants");
        consultant.setImagePath(imagePath);
        Consultant saved = repository.save(consultant);
        return findById(saved.getId());
    }

    private void applyFields(Consultant consultant, ConsultantDto dto) {
        Department department = departmentRepository.findById(dto.departmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found: " + dto.departmentId()));
        consultant.setName(dto.name());
        consultant.setDepartment(department);
        consultant.setSpecialization(resolveSpecialization(dto.specializationId()));
        consultant.setEmail(dto.email());
        consultant.setMobileNumber(dto.mobileNumber());
        consultant.setConsultationFee(dto.consultationFee() != null ? dto.consultationFee() : 0.0);
        consultant.setProfile(dto.profile());
        consultant.setAddress(dto.address());
        consultant.setAcceptingAppointments(dto.acceptingAppointments() == null || dto.acceptingAppointments());
        consultant.setQueueTokenPrefix(resolveQueueTokenPrefix(dto.queueTokenPrefix(), dto.name()));
        consultant.setConsultingRoomLabel(dto.consultingRoomLabel());
    }

    /**
     * Only actually overrides an existing custom prefix when the incoming
     * value is blank/null - the edit dialog always round-trips whatever
     * prefix it previously loaded, so this only fires in practice on first
     * creation (or if a user deliberately clears the field), never as a side
     * effect of unrelated name edits.
     */
    private String resolveQueueTokenPrefix(String requested, String name) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toUpperCase();
        }
        // Strips a leading "Dr."/"Dr " - every consultant's name starts with
        // that, so a naive first-character default would give every doctor
        // the same prefix ("D").
        String stripped = name.trim().replaceFirst("(?i)^dr\\.?\\s+", "");
        String initialSource = stripped.isEmpty() ? name.trim() : stripped;
        return initialSource.substring(0, 1).toUpperCase();
    }

    private Specialization resolveSpecialization(Long specializationId) {
        if (specializationId == null) {
            return null;
        }
        return specializationRepository.findById(specializationId)
                .orElseThrow(() -> new EntityNotFoundException("Specialization not found: " + specializationId));
    }

    private void recordAudit(Consultant consultant, String operation) {
        auditLogRepository.save(
                new ConsultantAuditLog(consultant.getId(), consultant.getName(), operation, currentUsername()));
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private List<ConsultantDto> toDtos(List<Consultant> consultants) {
        Map<Long, ConsultantAuditLog> createdBy = latestByConsultant(CREATE);
        Map<Long, ConsultantAuditLog> updatedBy = latestByConsultant(UPDATE);
        Map<Long, ConsultantAuditLog> deactivatedBy = latestByConsultant(DEACTIVATE);
        return consultants.stream().map(consultant -> toDto(consultant, createdBy, updatedBy, deactivatedBy)).toList();
    }

    /** Most recent log per consultant for the given operation, keyed by consultant id. */
    private Map<Long, ConsultantAuditLog> latestByConsultant(String operation) {
        return auditLogRepository.findAllByOperationOrderByPerformedAtDesc(operation).stream()
                .collect(HashMap::new, (map, log) -> map.putIfAbsent(log.getConsultantId(), log), HashMap::putAll);
    }

    private Consultant getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Consultant not found: " + id));
    }

    private ConsultantDto toDto(
            Consultant consultant,
            Map<Long, ConsultantAuditLog> createdBy,
            Map<Long, ConsultantAuditLog> updatedBy,
            Map<Long, ConsultantAuditLog> deactivatedBy) {
        ConsultantAuditLog created = createdBy.get(consultant.getId());
        ConsultantAuditLog updated = updatedBy.get(consultant.getId());
        ConsultantAuditLog deactivated = consultant.isActive() ? null : deactivatedBy.get(consultant.getId());
        Specialization specialization = consultant.getSpecialization();
        return new ConsultantDto(
                consultant.getId(),
                consultant.getName(),
                consultant.getDepartment().getId(),
                consultant.getDepartment().getName(),
                specialization != null ? specialization.getId() : null,
                specialization != null ? specialization.getName() : null,
                consultant.getEmail(),
                consultant.getMobileNumber(),
                consultant.getConsultationFee(),
                consultant.getProfile(),
                consultant.getAddress(),
                consultant.isAcceptingAppointments(),
                consultant.getImagePath(),
                consultant.getQueueTokenPrefix(),
                consultant.getConsultingRoomLabel(),
                consultant.isActive(),
                consultant.isPublishedToWeb(),
                consultant.getCreatedAt(),
                created != null ? created.getPerformedBy() : null,
                updated != null ? updated.getPerformedAt() : null,
                updated != null ? updated.getPerformedBy() : null,
                deactivated != null ? deactivated.getPerformedAt() : null,
                deactivated != null ? deactivated.getPerformedBy() : null);
    }
}
