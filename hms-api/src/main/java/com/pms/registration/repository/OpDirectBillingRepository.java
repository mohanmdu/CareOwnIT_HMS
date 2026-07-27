package com.pms.registration.repository;

import com.pms.registration.entity.OpDirectBilling;
import com.pms.registration.entity.PaymentMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpDirectBillingRepository extends JpaRepository<OpDirectBilling, Long> {
    Optional<OpDirectBilling> findByInvoiceNumber(Long invoiceNumber);

    // Patient Information (OP/IP) screen's Billing Details tab.
    List<OpDirectBilling> findByPatientIdOrderByBilledAtDesc(Long patientId);

    // Seeds InvoiceNumberService's shared sequence at startup, alongside Appointment's.
    @Query("SELECT MAX(b.invoiceNumber) FROM OpDirectBilling b")
    Long findMaxInvoiceNumber();

    @Query("""
            SELECT b FROM OpDirectBilling b
            WHERE (:fromInstant IS NULL OR b.billedAt >= :fromInstant)
              AND (:toInstant IS NULL OR b.billedAt < :toInstant)
              AND (:consultantId IS NULL OR b.consultant.id = :consultantId)
              AND (:paymentMode IS NULL OR b.paymentMode = :paymentMode)
            ORDER BY b.billedAt DESC
            """)
    List<OpDirectBilling> collectionReport(
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            @Param("consultantId") Long consultantId,
            @Param("paymentMode") PaymentMode paymentMode);

    // Patient Prescription worklist - walk-in visits with a consultant
    // captured, listed alongside Appointment rows (see OpCaseSheetService).
    // Same three-field free-text match as AppointmentRepository.prescriptionWorklist().
    @Query("""
            SELECT b FROM OpDirectBilling b
            WHERE (:fromInstant IS NULL OR b.billedAt >= :fromInstant)
              AND (:toInstant IS NULL OR b.billedAt < :toInstant)
              AND (:consultantId IS NULL OR b.consultant.id = :consultantId)
              AND (:search IS NULL OR :search = ''
                   OR LOWER(b.patient.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(b.patient.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(b.patient.registrationNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR b.patient.mobileNumber LIKE CONCAT('%', :search, '%'))
            ORDER BY b.billedAt DESC
            """)
    List<OpDirectBilling> prescriptionWorklist(
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            @Param("consultantId") Long consultantId,
            @Param("search") String search);
}
