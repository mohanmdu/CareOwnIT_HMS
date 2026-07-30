package com.pms.registration.repository;

import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.AppointmentStatus;
import com.pms.registration.entity.PaymentMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByAppointmentDate(LocalDate appointmentDate);

    // Payment Refund's search-by-invoice-number step.
    Optional<Appointment> findByInvoiceNumber(Long invoiceNumber);

    // ICD Code Search's patient-visit-summary column (most recent OP visit).
    Optional<Appointment> findFirstByPatientIdOrderByAppointmentDateDescSlotTimeDesc(Long patientId);

    // Excludes CANCELLED so a cancelled appointment doesn't permanently block
    // re-booking the same slot (book()'s collision guard).
    List<Appointment> findByConsultantIdAndAppointmentDateAndSlotTimeAndStatusNot(
            Long consultantId, LocalDate appointmentDate, java.time.LocalTime slotTime, AppointmentStatus excludedStatus);

    List<Appointment> findByConsultantIdAndAppointmentDateAndStatusNot(
            Long consultantId, LocalDate appointmentDate, AppointmentStatus excludedStatus);

    // Night sessions wrap past midnight, so a single queried date's slots can
    // land on either that date or the next one - callers pass both.
    List<Appointment> findByConsultantIdAndAppointmentDateInAndStatusNot(
            Long consultantId, List<LocalDate> appointmentDates, AppointmentStatus excludedStatus);

    @Query("""
            SELECT a FROM Appointment a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:fromDate IS NULL OR a.appointmentDate >= :fromDate)
              AND (:toDate IS NULL OR a.appointmentDate <= :toDate)
              AND (:departmentId IS NULL OR a.consultant.department.id = :departmentId)
              AND (:patientId IS NULL OR a.patient.id = :patientId)
            ORDER BY a.appointmentDate DESC, a.slotTime DESC
            """)
    List<Appointment> search(
            @Param("status") AppointmentStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("departmentId") Long departmentId,
            @Param("patientId") Long patientId);

    // Seeds InvoiceNumberService's shared sequence at startup - MAX rather
    // than a row count, since the sequence is now shared with OP Direct
    // Billing and a simple count would no longer track the true high-water mark.
    @Query("SELECT MAX(a.invoiceNumber) FROM Appointment a")
    Long findMaxInvoiceNumber();

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status = com.pms.registration.entity.AppointmentStatus.COMPLETED
              AND (:fromInstant IS NULL OR a.billedAt >= :fromInstant)
              AND (:toInstant IS NULL OR a.billedAt < :toInstant)
              AND (:consultantId IS NULL OR a.consultant.id = :consultantId)
              AND (:paymentMode IS NULL OR a.paymentMode = :paymentMode)
            ORDER BY a.billedAt DESC
            """)
    List<Appointment> collectionReport(
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            @Param("consultantId") Long consultantId,
            @Param("paymentMode") PaymentMode paymentMode);

    // CEO/MD Dashboard OP Revenue's Consulting Fee slice - same billedAt-based
    // date semantic as collectionReport() above (revenue is recognized when
    // collected, not when the appointment happened).
    @Query("""
            SELECT COALESCE(SUM(a.paidAmount), 0) FROM Appointment a
            WHERE a.status = com.pms.registration.entity.AppointmentStatus.COMPLETED
              AND (:fromInstant IS NULL OR a.billedAt >= :fromInstant)
              AND (:toInstant IS NULL OR a.billedAt < :toInstant)
            """)
    double sumPaidAmountForDashboard(@Param("fromInstant") Instant fromInstant, @Param("toInstant") Instant toInstant);

    // CEO/MD Dashboard OP Dashboard's Total/New/Repeat Appointments - "New" is
    // a patient's chronologically first-ever appointment (any status but
    // CANCELLED), regardless of how long ago that first visit actually was.
    // Filtered by appointment_date (the visit itself), not billed_at (see
    // sumPaidAmountForDashboard) - this is an operational count, not revenue.
    @Query(value = """
            SELECT COUNT(*) AS total,
              COALESCE(SUM(CASE WHEN NOT EXISTS (
                SELECT 1 FROM appointment a2 WHERE a2.patient_id = a.patient_id AND a2.status <> 'CANCELLED'
                  AND (a2.appointment_date < a.appointment_date OR (a2.appointment_date = a.appointment_date AND a2.id < a.id))
              ) THEN 1 ELSE 0 END), 0) AS newCount
            FROM appointment a
            WHERE a.status <> 'CANCELLED'
              AND (:fromDate IS NULL OR a.appointment_date >= :fromDate)
              AND (:toDate IS NULL OR a.appointment_date <= :toDate)
            """, nativeQuery = true)
    NewRepeatProjection countNewVsRepeat(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** Projection for countNewVsRepeat() - repeat = total - newCount, computed in the service. */
    interface NewRepeatProjection {
        long getTotal();

        long getNewCount();
    }

    // Patient Prescription worklist - excludes CANCELLED (nothing to document
    // for a cancelled visit) and matches free text against name/PID/mobile,
    // the same three fields PatientRepository.searchActive() matches.
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status <> com.pms.registration.entity.AppointmentStatus.CANCELLED
              AND (:fromDate IS NULL OR a.appointmentDate >= :fromDate)
              AND (:toDate IS NULL OR a.appointmentDate <= :toDate)
              AND (:consultantId IS NULL OR a.consultant.id = :consultantId)
              AND (:search IS NULL OR :search = ''
                   OR LOWER(a.patient.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(a.patient.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(a.patient.registrationNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR a.patient.mobileNumber LIKE CONCAT('%', :search, '%'))
            ORDER BY a.appointmentDate DESC, a.slotTime DESC
            """)
    List<Appointment> prescriptionWorklist(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("consultantId") Long consultantId,
            @Param("search") String search);

    // Doctor Queue Reception worklist's "still just Booked/Confirmed, no
    // queue entry yet" half of the union (see DoctorQueueService). Excludes
    // COMPLETED deliberately - an appointment can reach COMPLETED via
    // bill() with no check-in ever happening, and such a finished visit must
    // not resurface as a still-pending "Booked" row. The JPQL reference to
    // DoctorQueueEntry is a runtime Hibernate-metamodel reference only, not
    // a compile-time Java import.
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.consultant.id = :consultantId
              AND a.appointmentDate = :date
              AND a.status IN (com.pms.registration.entity.AppointmentStatus.BOOKED, com.pms.registration.entity.AppointmentStatus.CONFIRMED)
              AND NOT EXISTS (SELECT 1 FROM DoctorQueueEntry q WHERE q.appointment = a)
            ORDER BY a.slotTime ASC
            """)
    List<Appointment> findNotYetCheckedIn(@Param("consultantId") Long consultantId, @Param("date") LocalDate date);
}
