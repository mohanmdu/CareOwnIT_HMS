package com.pms.masters.repository;

import com.pms.masters.entity.GeneralUserAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralUserAuditLogRepository extends JpaRepository<GeneralUserAuditLog, Long> {
    List<GeneralUserAuditLog> findAllByOperationOrderByPerformedAtDesc(String operation);

    List<GeneralUserAuditLog> findAllByOrderByPerformedAtDesc();
}
