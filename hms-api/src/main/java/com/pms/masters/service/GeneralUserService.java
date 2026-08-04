package com.pms.masters.service;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.dto.GeneralUserAuditLogDto;
import com.pms.masters.dto.GeneralUserDto;
import com.pms.masters.entity.GeneralUser;
import com.pms.masters.entity.GeneralUserAuditLog;
import com.pms.masters.entity.Role;
import com.pms.masters.repository.GeneralUserAuditLogRepository;
import com.pms.masters.repository.GeneralUserRepository;
import com.pms.masters.repository.RoleRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create/deactivate/restore are written to GeneralUserAuditLog (mirroring
 * DepartmentService/ConsultantService) so the list screen can show who
 * created and who deactivated each user. Since V73, this is also where real
 * login credentials are created/reset - see applyFields() vs resetPassword()
 * for why password changes are a separate, deliberate action rather than
 * folded into the general update() path (mirrors the same reasoning as
 * IpBillingCategoryService.update() vs updateRevenueBucket() from the CEO
 * dashboard work - a plain profile edit must never silently reset a
 * sensitive field the caller didn't mean to touch).
 *
 * No client-scoping here - see GeneralUser's own doc comment. Each tenant
 * has its own dedicated database (Phase B), so every row in this table
 * already belongs to exactly one client by construction.
 */
@Service
@Transactional(readOnly = true)
public class GeneralUserService {

    private static final String CREATE = "CREATE";
    private static final String UPDATE = "UPDATE";
    private static final String DEACTIVATE = "DEACTIVATE";
    private static final String RESTORE = "RESTORE";
    private static final String RESET_PASSWORD = "RESET_PASSWORD";

    private final GeneralUserRepository repository;
    private final RoleRepository roleRepository;
    private final GeneralUserAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public GeneralUserService(
            GeneralUserRepository repository,
            RoleRepository roleRepository,
            GeneralUserAuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<GeneralUserDto> findActive() {
        return toDtos(repository.findByActiveTrueOrderByIdAsc());
    }

    public List<GeneralUserDto> findInactive() {
        return toDtos(repository.findByActiveFalseOrderByUpdatedAtDesc());
    }

    public List<GeneralUserAuditLogDto> auditLogs() {
        return auditLogRepository.findAllByOrderByPerformedAtDesc().stream()
                .map(log -> new GeneralUserAuditLogDto(
                        log.getId(), log.getOperation(), log.getGeneralUserName(), log.getPerformedBy(), log.getPerformedAt()))
                .toList();
    }

    public GeneralUserDto findById(Long id) {
        return toDto(getOrThrow(id), latestByUser(CREATE), latestByUser(DEACTIVATE));
    }

    @Transactional
    public GeneralUserDto create(GeneralUserDto dto) {
        if (dto.initialPassword() == null || dto.initialPassword().isBlank()) {
            throw new IllegalArgumentException("An initial password is required to create a login.");
        }
        if (repository.existsByUsernameIgnoreCase(dto.username())) {
            throw new IllegalArgumentException("Username already taken: " + dto.username());
        }
        GeneralUser user = new GeneralUser();
        applyFields(user, dto);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(dto.initialPassword()));
        user.setMustChangePassword(true);
        GeneralUser saved = repository.save(user);
        recordAudit(saved, CREATE);
        return findById(saved.getId());
    }

    @Transactional
    public GeneralUserDto update(Long id, GeneralUserDto dto) {
        GeneralUser user = getOrThrow(id);
        if (!user.getUsername().equalsIgnoreCase(dto.username()) && repository.existsByUsernameIgnoreCase(dto.username())) {
            throw new IllegalArgumentException("Username already taken: " + dto.username());
        }
        applyFields(user, dto);
        GeneralUser saved = repository.save(user);
        recordAudit(saved, UPDATE);
        return findById(saved.getId());
    }

    /** Admin-initiated reset (e.g. the user forgot their password) - deliberately separate from update() so a plain profile edit never silently touches credentials. */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password must not be blank.");
        }
        GeneralUser user = getOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        repository.save(user);
        recordAudit(user, RESET_PASSWORD);
    }

    @Transactional
    public void deactivate(Long id) {
        GeneralUser user = getOrThrow(id);
        user.setActive(false);
        repository.save(user);
        recordAudit(user, DEACTIVATE);
    }

    @Transactional
    public void restore(Long id) {
        GeneralUser user = getOrThrow(id);
        user.setActive(true);
        repository.save(user);
        recordAudit(user, RESTORE);
    }

    private void applyFields(GeneralUser user, GeneralUserDto dto) {
        Role role = roleRepository.findById(dto.roleId())
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + dto.roleId()));
        user.setName(dto.name());
        user.setMobileNumber(dto.mobileNumber());
        user.setEmail(dto.email());
        user.setRole(role);
        user.setUsername(dto.username());
    }

    private void recordAudit(GeneralUser user, String operation) {
        auditLogRepository.save(new GeneralUserAuditLog(user.getId(), user.getName(), operation, currentUsername()));
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private List<GeneralUserDto> toDtos(List<GeneralUser> users) {
        Map<Long, GeneralUserAuditLog> createdBy = latestByUser(CREATE);
        Map<Long, GeneralUserAuditLog> deactivatedBy = latestByUser(DEACTIVATE);
        return users.stream().map(user -> toDto(user, createdBy, deactivatedBy)).toList();
    }

    /** Most recent log per user for the given operation, keyed by user id. */
    private Map<Long, GeneralUserAuditLog> latestByUser(String operation) {
        return auditLogRepository.findAllByOperationOrderByPerformedAtDesc(operation).stream()
                .collect(HashMap::new, (map, log) -> map.putIfAbsent(log.getGeneralUserId(), log), HashMap::putAll);
    }

    private GeneralUser getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("General user not found: " + id));
    }

    private GeneralUserDto toDto(
            GeneralUser user, Map<Long, GeneralUserAuditLog> createdBy, Map<Long, GeneralUserAuditLog> deactivatedBy) {
        GeneralUserAuditLog created = createdBy.get(user.getId());
        GeneralUserAuditLog deactivated = user.isActive() ? null : deactivatedBy.get(user.getId());
        return new GeneralUserDto(
                user.getId(),
                user.getName(),
                user.getMobileNumber(),
                user.getEmail(),
                user.getRole().getId(),
                user.getRole().getName(),
                user.isActive(),
                user.getUsername(),
                null,
                user.isMustChangePassword(),
                user.getCreatedAt(),
                created != null ? created.getPerformedBy() : null,
                deactivated != null ? deactivated.getPerformedAt() : null,
                deactivated != null ? deactivated.getPerformedBy() : null);
    }
}
