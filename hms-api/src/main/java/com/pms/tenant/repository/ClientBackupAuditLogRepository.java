package com.pms.tenant.repository;

import com.pms.tenant.entity.ClientBackupAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientBackupAuditLogRepository extends JpaRepository<ClientBackupAuditLog, Long> {

    List<ClientBackupAuditLog> findByClientIdOrderByPerformedAtDesc(Long clientId);
}
