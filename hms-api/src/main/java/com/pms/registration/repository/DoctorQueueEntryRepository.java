package com.pms.registration.repository;

import com.pms.registration.entity.DoctorQueueEntry;
import com.pms.registration.entity.DoctorQueueStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorQueueEntryRepository extends JpaRepository<DoctorQueueEntry, Long> {

    Optional<DoctorQueueEntry> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    // Full day's picture for the Reception worklist and audit screen - every
    // status, including terminal ones, ordered by arrival/token order.
    List<DoctorQueueEntry> findByConsultantIdAndQueueDateOrderByTokenNumberAsc(Long consultantId, LocalDate queueDate);

    // The doctor dashboard's "currently called" slot, and nextPatient()'s
    // "someone is already called" guard. At most one row expected at rest.
    Optional<DoctorQueueEntry> findFirstByConsultantIdAndQueueDateAndStatusIn(
            Long consultantId, LocalDate queueDate, Collection<DoctorQueueStatus> statuses);

    // Single source of truth for BOTH "who is next" (nextPatient() takes the
    // head) and "the ordered waiting list" (the doctor dashboard shows this
    // whole list) - they can never disagree because they're the same query.
    // Emergency-priority entries sort first; within each priority tier, pure
    // FIFO by token-assignment time.
    @Query("""
            SELECT q FROM DoctorQueueEntry q
            WHERE q.consultant.id = :consultantId AND q.queueDate = :queueDate
              AND q.status = com.pms.registration.entity.DoctorQueueStatus.WAITING
            ORDER BY CASE WHEN q.priority = com.pms.registration.entity.DoctorQueuePriority.EMERGENCY THEN 0 ELSE 1 END,
                     q.checkedInAt ASC
            """)
    List<DoctorQueueEntry> findWaitingOrderedByPriority(@Param("consultantId") Long consultantId, @Param("queueDate") LocalDate queueDate);

    @Query("""
            SELECT q.status AS status, COUNT(q) AS total FROM DoctorQueueEntry q
            WHERE q.consultant.id = :consultantId AND q.queueDate = :queueDate
            GROUP BY q.status
            """)
    List<StatusCountProjection> countByStatus(@Param("consultantId") Long consultantId, @Param("queueDate") LocalDate queueDate);

    interface StatusCountProjection {
        DoctorQueueStatus getStatus();

        long getTotal();
    }
}
