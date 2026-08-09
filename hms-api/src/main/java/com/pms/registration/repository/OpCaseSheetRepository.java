package com.pms.registration.repository;

import com.pms.registration.entity.OpCaseSheet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpCaseSheetRepository extends JpaRepository<OpCaseSheet, Long> {
    Optional<OpCaseSheet> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    Optional<OpCaseSheet> findByOpDirectBillingId(Long opDirectBillingId);

    boolean existsByOpDirectBillingId(Long opDirectBillingId);

    // Scoped to appointment-linked case sheets only - the Review Date Report
    // screen/DTO (ReviewDateReportEntryDto.appointmentId) has no concept of a
    // Direct Billing walk-in source yet (unlike the Patient Prescription
    // worklist's appointmentId/directBillingId/source triad), so a walk-in's
    // case sheet is intentionally left out here for now rather than crashing
    // OpCaseSheetService.toReviewDateEntry() or silently mislabeling it.
    @Query("""
            SELECT c FROM OpCaseSheet c
            WHERE c.appointment IS NOT NULL
              AND c.reviewDate IS NOT NULL
              AND (:fromDate IS NULL OR c.reviewDate >= :fromDate)
              AND (:toDate IS NULL OR c.reviewDate <= :toDate)
              AND (:upcoming = TRUE AND c.reviewDate >= CURRENT_DATE
                   OR :upcoming = FALSE AND c.reviewDate < CURRENT_DATE)
            ORDER BY c.reviewDate ASC
            """)
    List<OpCaseSheet> reviewDateReport(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("upcoming") boolean upcoming);
}
