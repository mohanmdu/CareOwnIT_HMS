package com.pms.ipadmission.repository;

import com.pms.ipadmission.entity.Admission;
import com.pms.ipadmission.entity.AdmissionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {
    long countByStatus(AdmissionStatus status);

    // ICD Code Search's patient-visit-summary column (most recent IP visit).
    java.util.Optional<Admission> findFirstByPatientIdOrderByAdmissionDateDesc(Long patientId);

    // Resolves the specific admission a Lab Requisition should link to (LabRequisitionService.create()) -
    // deliberately scoped to ADMITTED only, not "most recent regardless of status" like the method above.
    java.util.Optional<Admission> findFirstByPatientIdAndStatusOrderByAdmissionDateDesc(Long patientId, AdmissionStatus status);

    // Patient Information (OP/IP) screen's Inpatient Details tab - full admission history, any status.
    List<Admission> findByPatientIdOrderByAdmissionDateDesc(Long patientId);

    @Query("""
            SELECT a FROM Admission a
            WHERE (:fromDate IS NULL OR a.admissionDate >= :fromDate)
              AND (:toDate IS NULL OR a.admissionDate < :toDate)
            ORDER BY a.admissionDate DESC
            """)
    List<Admission> findForAdmissionReport(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    // CEO/MD Dashboard Patients Admitted - a real COUNT twin of findForAdmissionReport
    // above (same WHERE clause), so the dashboard's hot path never fetches a full list
    // just to call .size() on it.
    @Query("""
            SELECT COUNT(a) FROM Admission a
            WHERE (:fromDate IS NULL OR a.admissionDate >= :fromDate)
              AND (:toDate IS NULL OR a.admissionDate < :toDate)
            """)
    long countForAdmissionReport(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    @Query("""
            SELECT a FROM Admission a
            WHERE a.status = com.pms.ipadmission.entity.AdmissionStatus.DISCHARGED
              AND (:fromDate IS NULL OR a.dischargeDate >= :fromDate)
              AND (:toDate IS NULL OR a.dischargeDate < :toDate)
            ORDER BY a.dischargeDate DESC
            """)
    List<Admission> findDischargedForList(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    // CEO/MD Dashboard Patients Discharged - COUNT twin of findDischargedForList above.
    @Query("""
            SELECT COUNT(a) FROM Admission a
            WHERE a.status = com.pms.ipadmission.entity.AdmissionStatus.DISCHARGED
              AND (:fromDate IS NULL OR a.dischargeDate >= :fromDate)
              AND (:toDate IS NULL OR a.dischargeDate < :toDate)
            """)
    long countDischargedForList(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    @Query("""
            SELECT a FROM Admission a
            WHERE a.status = com.pms.ipadmission.entity.AdmissionStatus.DISCHARGE_INITIATED
              AND (:fromDate IS NULL OR a.dischargeDate >= :fromDate)
              AND (:toDate IS NULL OR a.dischargeDate < :toDate)
            ORDER BY a.dischargeDate DESC
            """)
    List<Admission> findDischargeInitiatedForList(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    @Query("""
            SELECT a FROM Admission a
            WHERE a.status = com.pms.ipadmission.entity.AdmissionStatus.CANCELLED
              AND (:fromDate IS NULL OR a.cancelledAt >= :fromDate)
              AND (:toDate IS NULL OR a.cancelledAt <= :toDate)
            ORDER BY a.cancelledAt DESC
            """)
    List<Admission> findCancelledForList(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);
}
