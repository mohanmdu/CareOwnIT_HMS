package com.pms.masters.repository;

import com.pms.masters.entity.GeneralUserAuditLog;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralUserAuditLogRepository extends JpaRepository<GeneralUserAuditLog, Long> {
    List<GeneralUserAuditLog> findAllByOperationOrderByPerformedAtDesc(String operation);

    // Unscoped - only safe for internal use where the caller already
    // filters/looks up by a specific (already client-verified) user id, e.g.
    // GeneralUserService.latestByUser(). Never return this wholesale to a
    // client - see findAllByGeneralUserIdInOrderByPerformedAtDesc below for
    // the client-scoped equivalent used by the audit log screen itself.
    List<GeneralUserAuditLog> findAllByOrderByPerformedAtDesc();

    List<GeneralUserAuditLog> findAllByGeneralUserIdInOrderByPerformedAtDesc(Collection<Long> generalUserIds);
}
