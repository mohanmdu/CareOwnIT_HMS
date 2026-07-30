package com.pms.registration.repository;

import com.pms.registration.entity.DoctorQueueAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorQueueAuditLogRepository extends JpaRepository<DoctorQueueAuditLog, Long> {
    List<DoctorQueueAuditLog> findAllByOrderByPerformedAtDesc();

    List<DoctorQueueAuditLog> findByQueueEntryIdOrderByPerformedAtDesc(Long queueEntryId);
}
