package com.pms.lab.repository;

import com.pms.lab.entity.LabPaymentMode;
import com.pms.lab.entity.LabRequisition;
import com.pms.lab.entity.LabRequisitionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabRequisitionRepository extends JpaRepository<LabRequisition, Long> {
    List<LabRequisition> findByStatusOrderByRequisitionDateDesc(LabRequisitionStatus status);

    // Patient Information (OP/IP) screen's Investigations Done tab - full requisition history, any status.
    List<LabRequisition> findByPatientIdOrderByRequisitionDateDesc(Long patientId);

    // IP Billing ledger sync: every non-cancelled Lab/Investigations charge linked to this admission.
    List<LabRequisition> findByAdmissionIdAndStatusNotOrderByRequisitionDateAsc(Long admissionId, LabRequisitionStatus excludedStatus);

    // Requisition numbers are fixed-width (LABREQ00001), so lexicographic and
    // numeric ordering agree - mirrors PatientService.nextRegistrationNumber's
    // derive-from-the-table approach rather than a row count.
    Optional<LabRequisition> findTopByRequisitionNumberStartingWithOrderByRequisitionNumberDesc(String prefix);

    @Query("SELECT MAX(r.invoiceNumber) FROM LabRequisition r")
    Long findMaxInvoiceNumber();

    Optional<LabRequisition> findByInvoiceNumber(Long invoiceNumber);

    // Backs the three Collection Report screens (Summary/Lab Detail/
    // Investigation Detail) - requisitionType/consultantId/paymentMode/
    // categoryName are all optional filters, and categoryName is matched via
    // an EXISTS subquery against the item snapshot rather than a join, so a
    // requisition with several matching items still contributes only one row.
    @Query("""
            SELECT r FROM LabRequisition r
            WHERE r.status = :status
              AND (:from IS NULL OR r.approvedAt >= :from)
              AND (:to IS NULL OR r.approvedAt < :to)
              AND (:requisitionType IS NULL OR r.requisitionType = :requisitionType)
              AND (:consultantId IS NULL OR r.consultant.id = :consultantId)
              AND (:paymentMode IS NULL OR r.paymentMode = :paymentMode)
              AND (:categoryName IS NULL OR EXISTS (
                    SELECT 1 FROM LabRequisitionItem i WHERE i.requisition = r AND i.categoryName = :categoryName))
            ORDER BY r.approvedAt DESC
            """)
    List<LabRequisition> findForCollectionReport(
            @Param("status") LabRequisitionStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("requisitionType") String requisitionType,
            @Param("consultantId") Long consultantId,
            @Param("paymentMode") LabPaymentMode paymentMode,
            @Param("categoryName") String categoryName);

    // CEO/MD Dashboard IP Revenue's Lab & X-ray slice - combined (no
    // Lab-vs-Radiology split needed on the IP side, unlike OP - see the
    // reference image's IP Revenue legend).
    @Query("""
            SELECT COALESCE(SUM(r.totalAmount), 0) FROM LabRequisition r
            WHERE r.status = com.pms.lab.entity.LabRequisitionStatus.APPROVED
              AND r.patientType = 'IP'
              AND r.admission IS NOT NULL
              AND (:from IS NULL OR r.approvedAt >= :from)
              AND (:to IS NULL OR r.approvedAt < :to)
            """)
    double sumForIpDashboard(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
