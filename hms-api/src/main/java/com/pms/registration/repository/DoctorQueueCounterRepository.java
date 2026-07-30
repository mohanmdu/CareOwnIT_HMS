package com.pms.registration.repository;

import com.pms.registration.entity.DoctorQueueCounter;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorQueueCounterRepository extends JpaRepository<DoctorQueueCounter, Long> {

    // Idempotent - guarantees a row exists for (consultantId, queueDate)
    // regardless of the caller's transaction isolation level. INSERT IGNORE
    // silently no-ops on the duplicate-key path rather than erroring, so
    // this is always safe to call unconditionally before the locking SELECT
    // below.
    @Modifying
    @Query(
            value = """
            INSERT IGNORE INTO doctor_queue_counter (consultant_id, queue_date, last_token_number)
            VALUES (:consultantId, :queueDate, 0)
            """,
            nativeQuery = true)
    void ensureRowExists(@Param("consultantId") Long consultantId, @Param("queueDate") LocalDate queueDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DoctorQueueCounter c WHERE c.consultantId = :consultantId AND c.queueDate = :queueDate")
    Optional<DoctorQueueCounter> lockForUpdate(@Param("consultantId") Long consultantId, @Param("queueDate") LocalDate queueDate);
}
