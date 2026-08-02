package com.pms.masters.repository;

import com.pms.masters.entity.GeneralUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralUserRepository extends JpaRepository<GeneralUser, Long> {
    List<GeneralUser> findByActiveTrueOrderByIdAsc();

    List<GeneralUser> findByActiveFalseOrderByUpdatedAtDesc();

    // Client-scoped equivalents of the two above - what GeneralUserService's
    // list screens actually use now. The unscoped versions above are kept
    // only because nothing else currently calls them; do not reuse them for
    // anything client-facing.
    List<GeneralUser> findByClientIdAndActiveTrueOrderByIdAsc(Long clientId);

    List<GeneralUser> findByClientIdAndActiveFalseOrderByUpdatedAtDesc(Long clientId);

    // Every user (active or not) for a client - used to scope the audit log
    // query (GeneralUserAuditLog has no client_id column of its own).
    List<GeneralUser> findByClientId(Long clientId);

    // Real login lookup for single-tenant mode (the default - see
    // com.pms.tenant.DeploymentModeProperties) - safe as a global lookup
    // because that mode only ever has one Client, so this stays equivalent
    // to the client-scoped lookup below. See com.pms.security.LoginService.
    Optional<GeneralUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    // Client-scoped equivalents, used by multi-tenant-mode login and by
    // GeneralUserService's duplicate-username check (per-client uniqueness,
    // not global - see the multi-tenant licensing plan's Decisions
    // Confirmed §1).
    Optional<GeneralUser> findByClientIdAndUsernameIgnoreCase(Long clientId, String username);

    boolean existsByClientIdAndUsernameIgnoreCase(Long clientId, String username);
}
