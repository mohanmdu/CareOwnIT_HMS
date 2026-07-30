package com.pms.registration.service;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.Consultant;
import com.pms.masters.repository.ConsultantRepository;
import com.pms.registration.dto.AppointmentAuditLogDto;
import com.pms.registration.dto.AppointmentDto;
import com.pms.registration.dto.BillAppointmentRequest;
import com.pms.registration.dto.CollectionReportEntryDto;
import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.AppointmentAuditLog;
import com.pms.registration.entity.AppointmentStatus;
import com.pms.registration.entity.CancelledBy;
import com.pms.registration.entity.Patient;
import com.pms.registration.entity.PaymentMode;
import com.pms.registration.entity.OpDirectBilling;
import com.pms.registration.repository.AppointmentAuditLogRepository;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.OpDirectBillingRepository;
import com.pms.registration.repository.PatientRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Replaces the legacy appointment booking family (newAppointmentaction,
 * appointReqt, confirmreq) and collapses the ~10 near-duplicate cancel
 * methods in AdminAction into one cancel operation with a required reason
 * (migration doc §4.1).
 */
@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository repository;
    private final PatientRepository patientRepository;
    private final ConsultantRepository consultantRepository;
    private final AppointmentAuditLogRepository auditLogRepository;
    private final OpDirectBillingRepository opDirectBillingRepository;
    private final ObjectMapper objectMapper;
    private final InvoiceNumberService invoiceNumberService;
    private final DoctorQueueService doctorQueueService;

    public AppointmentService(
            AppointmentRepository repository,
            PatientRepository patientRepository,
            ConsultantRepository consultantRepository,
            AppointmentAuditLogRepository auditLogRepository,
            OpDirectBillingRepository opDirectBillingRepository,
            ObjectMapper objectMapper,
            InvoiceNumberService invoiceNumberService,
            DoctorQueueService doctorQueueService) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.consultantRepository = consultantRepository;
        this.auditLogRepository = auditLogRepository;
        this.opDirectBillingRepository = opDirectBillingRepository;
        this.objectMapper = objectMapper;
        this.invoiceNumberService = invoiceNumberService;
        this.doctorQueueService = doctorQueueService;
    }

    public List<AppointmentDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public List<AppointmentDto> search(
            AppointmentStatus status, LocalDate fromDate, LocalDate toDate, Long departmentId, Long patientId) {
        return repository.search(status, fromDate, toDate, departmentId, patientId).stream().map(this::toDto).toList();
    }

    public AppointmentDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public AppointmentDto book(AppointmentDto dto) {
        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + dto.patientId()));
        Consultant consultant = consultantRepository.findById(dto.consultantId())
                .orElseThrow(() -> new EntityNotFoundException("Consultant not found: " + dto.consultantId()));

        // Defensive last-write-wins guard: the frontend only ever submits a slot
        // that /slots reported as AVAILABLE, so this should only fire on a rare
        // concurrent-booking race. Excludes CANCELLED - a cancelled appointment
        // must not block re-booking the same slot.
        boolean slotTaken = !repository
                .findByConsultantIdAndAppointmentDateAndSlotTimeAndStatusNot(
                        dto.consultantId(), dto.appointmentDate(), dto.slotTime(), AppointmentStatus.CANCELLED)
                .isEmpty();
        if (slotTaken) {
            throw new IllegalArgumentException(
                    "Consultant already has an appointment at " + dto.slotTime() + " on " + dto.appointmentDate());
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setConsultant(consultant);
        appointment.setAppointmentDate(dto.appointmentDate());
        appointment.setSlotTime(dto.slotTime());
        appointment.setNotes(dto.notes());
        appointment.setStatus(AppointmentStatus.BOOKED);
        Appointment saved = repository.save(appointment);
        recordAudit("CREATE", null, saved);
        return toDto(saved);
    }

    @Transactional
    public AppointmentDto confirm(Long id) {
        Appointment appointment = getOrThrow(id);
        AppointmentAuditSnapshot before = snapshot(appointment);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = repository.save(appointment);
        recordAudit("CONFIRM", before, saved);
        return toDto(saved);
    }

    @Transactional
    public AppointmentDto cancel(Long id, String reason, CancelledBy cancelledBy) {
        Appointment appointment = getOrThrow(id);
        AppointmentAuditSnapshot before = snapshot(appointment);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(reason);
        appointment.setCancelledBy(cancelledBy);
        Appointment saved = repository.save(appointment);
        recordAudit("CANCEL", before, saved);
        // A cancelled appointment must not leave a ghost token in the
        // doctor's live queue - no-op if no active queue entry exists (most
        // cancelled appointments never had one).
        doctorQueueService.cascadeCancelForAppointment(saved.getId(), reason);
        return toDto(saved);
    }

    /**
     * Approving an appointment goes straight to billing rather than a bare
     * status flip: recording payment here moves BOOKED -> COMPLETED in one
     * step, stamps a sequential Invoice No (for the Collection Report and any
     * future refund flow) and who processed it. If billing is never
     * completed the appointment simply stays BOOKED (Pending) - there's no
     * separate "approved but unbilled" state.
     */
    @Transactional
    public AppointmentDto bill(Long id, BillAppointmentRequest request) {
        Appointment appointment = getOrThrow(id);
        AppointmentAuditSnapshot before = snapshot(appointment);
        appointment.setPaidAmount(request.paidAmount());
        appointment.setDiscountAmount(request.discountAmount() != null ? request.discountAmount() : 0.0);
        appointment.setDoctorReferralAmount(
                request.doctorReferralAmount() != null ? request.doctorReferralAmount() : 0.0);
        appointment.setPaymentMode(request.paymentMode());
        appointment.setBillingRemarks(request.remarks());
        appointment.setBilledAt(Instant.now());
        appointment.setInvoiceNumber(invoiceNumberService.next());
        appointment.setBilledBy(currentUsername());
        appointment.setStatus(AppointmentStatus.COMPLETED);
        Appointment saved = repository.save(appointment);
        recordAudit("BILL", before, saved);
        return toDto(saved);
    }

    /**
     * Preview of the Invoice No the next bill() call will assign - shown in
     * the Approve Appointment dialog before the staff submits, so they can
     * see it alongside the invoiced amount rather than only after payment is
     * recorded. Doesn't reserve the number: if two appointments are approved
     * concurrently, whichever submits first gets it and the other's preview
     * is stale by one - acceptable for this single-cashier local-dev flow.
     */
    public long peekNextInvoiceNumber() {
        return invoiceNumberService.peekNext();
    }

    /**
     * Merges two revenue sources into one report: appointment billing and OP
     * Direct Billing (walk-in charges, optionally attributed to a
     * consultant - see OpDirectBilling.consultant). A consultantId filter
     * now matches both sources, since a walk-in can have one too.
     */
    public List<CollectionReportEntryDto> collectionReport(
            LocalDate fromDate, LocalDate toDate, Long consultantId, PaymentMode paymentMode) {
        Instant fromInstant = fromDate != null ? fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Instant toInstant =
                toDate != null ? toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Stream<CollectionReportEntryDto> appointmentEntries =
                repository.collectionReport(fromInstant, toInstant, consultantId, paymentMode).stream()
                        .map(this::toCollectionReportEntry);
        Stream<CollectionReportEntryDto> directBillingEntries =
                opDirectBillingRepository.collectionReport(fromInstant, toInstant, consultantId, paymentMode).stream()
                        .map(this::toCollectionReportEntry);
        return Stream.concat(appointmentEntries, directBillingEntries)
                .sorted(Comparator.comparing(CollectionReportEntryDto::billedAt).reversed())
                .toList();
    }

    public List<AppointmentAuditLogDto> auditLogs() {
        return auditLogRepository.findAllByOrderByPerformedAtDesc().stream().map(this::toAuditLogDto).toList();
    }

    /** The subset of Appointment fields that actually change across book/confirm/cancel/bill. */
    private record AppointmentAuditSnapshot(
            AppointmentStatus status,
            LocalDate appointmentDate,
            LocalTime slotTime,
            Double paidAmount,
            Double discountAmount,
            Double doctorReferralAmount,
            PaymentMode paymentMode,
            String cancellationReason,
            CancelledBy cancelledBy,
            Long invoiceNumber) {
    }

    private AppointmentAuditSnapshot snapshot(Appointment appointment) {
        return new AppointmentAuditSnapshot(
                appointment.getStatus(),
                appointment.getAppointmentDate(),
                appointment.getSlotTime(),
                appointment.getPaidAmount(),
                appointment.getDiscountAmount(),
                appointment.getDoctorReferralAmount(),
                appointment.getPaymentMode(),
                appointment.getCancellationReason(),
                appointment.getCancelledBy(),
                appointment.getInvoiceNumber());
    }

    /**
     * Writes one AppointmentAuditLog row per lifecycle change (see
     * migration doc's Appointment Audit Logs screen). before is null for a
     * brand-new booking (CREATE); otherwise it's a snapshot taken just
     * before the mutating setters ran in the calling method.
     */
    private void recordAudit(String operation, AppointmentAuditSnapshot before, Appointment after) {
        Patient patient = after.getPatient();
        Consultant consultant = after.getConsultant();
        AppointmentAuditLog log = new AppointmentAuditLog();
        log.setAppointment(after);
        log.setOperation(operation);
        log.setPatientName((patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim());
        log.setConsultantName(consultant.getName());
        log.setDepartmentName(consultant.getDepartment().getName());
        log.setAppointmentDate(after.getAppointmentDate());
        log.setSlotTime(after.getSlotTime());
        log.setChannel("web");
        log.setPreviousValue(before != null ? toJson(before) : null);
        log.setNewValue(toJson(snapshot(after)));
        log.setPerformedBy(currentUsername());
        log.setPerformedAt(Instant.now());
        auditLogRepository.save(log);
    }

    private String toJson(AppointmentAuditSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            // Serialization failing shouldn't fail the appointment operation
            // it's attached to - the log row just gets a null snapshot.
            return null;
        }
    }

    private AppointmentAuditLogDto toAuditLogDto(AppointmentAuditLog log) {
        return new AppointmentAuditLogDto(
                log.getId(),
                log.getOperation(),
                log.getPatientName(),
                log.getConsultantName(),
                log.getDepartmentName(),
                log.getAppointmentDate(),
                log.getSlotTime(),
                log.getChannel(),
                log.getPreviousValue(),
                log.getNewValue(),
                log.getPerformedBy(),
                log.getPerformedAt());
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private Appointment getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
    }

    private AppointmentDto toDto(Appointment appointment) {
        Consultant consultant = appointment.getConsultant();
        Patient patient = appointment.getPatient();
        return new AppointmentDto(
                appointment.getId(),
                patient.getId(),
                patient.getRegistrationNumber(),
                (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim(),
                patient.getAge(),
                patient.getGender(),
                consultant.getId(),
                consultant.getName(),
                consultant.getDepartment().getName(),
                appointment.getAppointmentDate(),
                appointment.getSlotTime(),
                appointment.getStatus(),
                appointment.getNotes(),
                appointment.getCancellationReason(),
                appointment.getCancelledBy(),
                consultant.getConsultationFee(),
                appointment.getPaidAmount(),
                appointment.getDiscountAmount(),
                appointment.getDoctorReferralAmount(),
                appointment.getPaymentMode(),
                appointment.getBillingRemarks(),
                appointment.getBilledAt(),
                appointment.getInvoiceNumber(),
                appointment.getBilledBy(),
                appointment.getRefundAmount());
    }

    private CollectionReportEntryDto toCollectionReportEntry(Appointment appointment) {
        Patient patient = appointment.getPatient();
        return new CollectionReportEntryDto(
                "APPOINTMENT",
                appointment.getId(),
                null,
                appointment.getInvoiceNumber(),
                (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim(),
                patient.getRegistrationNumber(),
                appointment.getBilledAt(),
                appointment.getPaymentMode(),
                appointment.getConsultant().getName(),
                appointment.getBilledBy(),
                appointment.getConsultant().getConsultationFee(),
                appointment.getDoctorReferralAmount(),
                appointment.getPaidAmount(),
                appointment.getRefundAmount());
    }

    private CollectionReportEntryDto toCollectionReportEntry(OpDirectBilling billing) {
        Patient patient = billing.getPatient();
        return new CollectionReportEntryDto(
                "DIRECT_BILLING",
                null,
                billing.getId(),
                billing.getInvoiceNumber(),
                (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim(),
                patient.getRegistrationNumber(),
                billing.getBilledAt(),
                billing.getPaymentMode(),
                billing.getConsultant() != null ? billing.getConsultant().getName() : null,
                billing.getBilledBy(),
                billing.getTotalAmount(),
                0.0,
                billing.getTotalAmount(),
                billing.getRefundAmount());
    }
}
