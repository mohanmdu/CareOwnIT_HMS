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
import com.pms.tenant.repository.ClientRepository;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ClientRepository clientRepository;
    private final HttpServletRequest request;

    public GeneralUserService(
            GeneralUserRepository repository,
            RoleRepository roleRepository,
            GeneralUserAuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            ClientRepository clientRepository,
            HttpServletRequest request) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.clientRepository = clientRepository;
        this.request = request;
    }

    public List<GeneralUserDto> findActive() {
        return toDtos(repository.findByClientIdAndActiveTrueOrderByIdAsc(currentClientId()));
    }

    public List<GeneralUserDto> findInactive() {
        return toDtos(repository.findByClientIdAndActiveFalseOrderByUpdatedAtDesc(currentClientId()));
    }

    public List<GeneralUserAuditLogDto> auditLogs() {
        List<Long> clientUserIds = repository.findByClientId(currentClientId()).stream().map(GeneralUser::getId).toList();
        return auditLogRepository.findAllByGeneralUserIdInOrderByPerformedAtDesc(clientUserIds).stream()
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
        Long clientId = currentClientId();
        if (repository.existsByClientIdAndUsernameIgnoreCase(clientId, dto.username())) {
            throw new IllegalArgumentException("Username already taken: " + dto.username());
        }
        GeneralUser user = new GeneralUser();
        user.setClient(clientRepository.getReferenceById(clientId));
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
        if (!user.getUsername().equalsIgnoreCase(dto.username())
                && repository.existsByClientIdAndUsernameIgnoreCase(user.getClient().getId(), dto.username())) {
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
        // Without this check, a client could assign a role belonging to a
        // different client to their own users - Role now carries client_id
        // too (see V90), so this needs the same ownership check as
        // getOrThrow() below, just inline since applyFields() isn't
        // fetching by Role id via that helper.
        if (!role.getClient().getId().equals(currentClientId())) {
            throw new EntityNotFoundException("Role not found: " + dto.roleId());
        }
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

    /**
     * The authenticated request's client id, straight from the verified
     * JWT's clientId claim (see JwtAuthenticationFilter, which stamps this
     * request attribute on every tenant request in both deployment modes).
     * Deliberately NOT re-derived by looking the caller up by username -
     * usernames are only unique per-client (see the multi-tenant licensing
     * plan's Decisions Confirmed §1), so two different clients' admins can
     * share a username (e.g. both named "admin"), which would make a
     * username-based lookup here ambiguous or, worse, silently resolve to
     * the wrong client.
     */
    private Long currentClientId() {
        Long clientId = (Long) request.getAttribute("clientId");
        if (clientId == null) {
            throw new IllegalStateException("No client context on this request.");
        }
        return clientId;
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

    /** Every id-based operation (findById/update/resetPassword/deactivate/restore) funnels through here, so this single ownership check is what stops one client from reading or modifying another client's users - a 404, not a 403, so a guessed/enumerated id from another client doesn't even confirm it exists. */
    private GeneralUser getOrThrow(Long id) {
        GeneralUser user = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("General user not found: " + id));
        if (!user.getClient().getId().equals(currentClientId())) {
            throw new EntityNotFoundException("General user not found: " + id);
        }
        return user;
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
