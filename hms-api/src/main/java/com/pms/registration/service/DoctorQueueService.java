package com.pms.registration.service;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.Consultant;
import com.pms.masters.repository.ConsultantRepository;
import com.pms.registration.dto.CheckInRequest;
import com.pms.registration.dto.DoctorQueueAuditLogDto;
import com.pms.registration.dto.DoctorQueueDashboardDto;
import com.pms.registration.dto.DoctorQueueEntryDto;
import com.pms.registration.dto.DoctorQueueNowServingDto;
import com.pms.registration.dto.NoShowForAppointmentRequest;
import com.pms.registration.dto.ReceptionWorklistEntryDto;
import com.pms.registration.dto.WalkInRegistrationRequest;
import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.AppointmentAuditLog;
import com.pms.registration.entity.AppointmentStatus;
import com.pms.registration.entity.DoctorQueueAuditLog;
import com.pms.registration.entity.DoctorQueueEntry;
import com.pms.registration.entity.DoctorQueuePriority;
import com.pms.registration.entity.DoctorQueueSource;
import com.pms.registration.entity.DoctorQueueStatus;
import com.pms.registration.entity.Patient;
import com.pms.registration.repository.AppointmentAuditLogRepository;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.DoctorQueueAuditLogRepository;
import com.pms.registration.repository.DoctorQueueEntryRepository;
import com.pms.registration.repository.DoctorQueueEntryRepository.StatusCountProjection;
import com.pms.registration.repository.PatientRepository;
import com.pms.settings.service.ClinicSettingsService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Mirrors AppointmentService's shape: snapshot-before / mutate / save /
 * recordAudit(operation, before, after) per mutating method. "BOOKED" is
 * deliberately never a DoctorQueueStatus value - receptionWorklist() merges
 * real DoctorQueueEntry rows with virtual "still just booked, no queue entry
 * yet" Appointment rows at the DTO level, the same way
 * AppointmentService.collectionReport() merges Appointment + OpDirectBilling.
 */
@Service
@Transactional(readOnly = true)
public class DoctorQueueService {

    private final DoctorQueueEntryRepository repository;
    private final DoctorQueueAuditLogRepository auditLogRepository;
    private final DoctorQueueTokenService tokenService;
    private final ConsultantRepository consultantRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentAuditLogRepository appointmentAuditLogRepository;
    private final ClinicSettingsService clinicSettingsService;
    private final ObjectMapper objectMapper;

    public DoctorQueueService(
            DoctorQueueEntryRepository repository,
            DoctorQueueAuditLogRepository auditLogRepository,
            DoctorQueueTokenService tokenService,
            ConsultantRepository consultantRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            AppointmentAuditLogRepository appointmentAuditLogRepository,
            ClinicSettingsService clinicSettingsService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.tokenService = tokenService;
        this.consultantRepository = consultantRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentAuditLogRepository = appointmentAuditLogRepository;
        this.clinicSettingsService = clinicSettingsService;
        this.objectMapper = objectMapper;
    }

    // ---------- Reads ----------

    public List<ReceptionWorklistEntryDto> receptionWorklist(Long consultantId, LocalDate date) {
        Consultant consultant = getConsultantOrThrow(consultantId);

        Stream<ReceptionWorklistEntryDto> virtualBooked = appointmentRepository
                .findNotYetCheckedIn(consultantId, date).stream()
                .map(appointment -> toVirtualWorklistDto(appointment, consultant));

        Stream<ReceptionWorklistEntryDto> realEntries = repository
                .findByConsultantIdAndQueueDateOrderByTokenNumberAsc(consultantId, date).stream()
                .map(this::toWorklistDto);

        return Stream.concat(virtualBooked, realEntries)
                .sorted(Comparator.comparing(ReceptionWorklistEntryDto::sortTimestamp))
                .toList();
    }

    public DoctorQueueDashboardDto dashboard(Long consultantId, LocalDate date) {
        Consultant consultant = getConsultantOrThrow(consultantId);

        DoctorQueueEntryDto currentCalled = repository
                .findFirstByConsultantIdAndQueueDateAndStatusIn(
                        consultantId, date, EnumSet.of(DoctorQueueStatus.CALLED, DoctorQueueStatus.IN_CONSULTATION))
                .map(this::toDto)
                .orElse(null);

        List<DoctorQueueEntryDto> waiting =
                repository.findWaitingOrderedByPriority(consultantId, date).stream().map(this::toDto).toList();

        Map<DoctorQueueStatus, Long> counts = repository.countByStatus(consultantId, date).stream()
                .collect(Collectors.toMap(StatusCountProjection::getStatus, StatusCountProjection::getTotal));

        long waitingCount = counts.getOrDefault(DoctorQueueStatus.WAITING, 0L);
        long calledCount = counts.getOrDefault(DoctorQueueStatus.CALLED, 0L)
                + counts.getOrDefault(DoctorQueueStatus.IN_CONSULTATION, 0L);
        long completedCount = counts.getOrDefault(DoctorQueueStatus.COMPLETED, 0L);
        long skippedCount = counts.getOrDefault(DoctorQueueStatus.SKIPPED, 0L);
        long noShowCount = counts.getOrDefault(DoctorQueueStatus.NO_SHOW, 0L);
        long cancelledCount = counts.getOrDefault(DoctorQueueStatus.CANCELLED, 0L);
        long totalToday = counts.values().stream().mapToLong(Long::longValue).sum();

        return new DoctorQueueDashboardDto(
                consultant.getId(),
                consultant.getName(),
                date,
                currentCalled,
                waiting,
                waitingCount,
                calledCount,
                completedCount,
                skippedCount,
                noShowCount,
                cancelledCount,
                totalToday);
    }

    /**
     * Public, PHI-free view for the Doctor Queue display board (see
     * PublicDoctorQueueController) - reuses the same two repository calls
     * dashboard() does, just projected down to a token number instead of
     * full patient DTOs. Returns moduleEnabled=false (rather than throwing)
     * when the add-on is turned off, so the board can render a friendly
     * message instead of an error.
     */
    public DoctorQueueNowServingDto boardView(Long consultantId, LocalDate date) {
        if (!clinicSettingsService.get().doctorQueueEnabled()) {
            return new DoctorQueueNowServingDto(consultantId, null, null, date, null, null, 0L, false);
        }

        Consultant consultant = getConsultantOrThrow(consultantId);

        DoctorQueueEntry currentCalled = repository
                .findFirstByConsultantIdAndQueueDateAndStatusIn(
                        consultantId, date, EnumSet.of(DoctorQueueStatus.CALLED, DoctorQueueStatus.IN_CONSULTATION))
                .orElse(null);

        Map<DoctorQueueStatus, Long> counts = repository.countByStatus(consultantId, date).stream()
                .collect(Collectors.toMap(StatusCountProjection::getStatus, StatusCountProjection::getTotal));
        long waitingCount = counts.getOrDefault(DoctorQueueStatus.WAITING, 0L);

        return new DoctorQueueNowServingDto(
                consultant.getId(),
                consultant.getName(),
                consultant.getConsultingRoomLabel(),
                date,
                currentCalled != null ? currentCalled.getTokenDisplay() : null,
                currentCalled != null ? currentCalled.getPriority() : null,
                waitingCount,
                true);
    }

    public DoctorQueueEntryDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    public List<DoctorQueueAuditLogDto> auditLogs() {
        return auditLogRepository.findAllByOrderByPerformedAtDesc().stream().map(this::toAuditDto).toList();
    }

    public List<DoctorQueueAuditLogDto> auditLogsForEntry(Long id) {
        return auditLogRepository.findByQueueEntryIdOrderByPerformedAtDesc(id).stream().map(this::toAuditDto).toList();
    }

    // ---------- Mutations ----------

    @Transactional
    public DoctorQueueEntryDto checkIn(CheckInRequest request) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId())
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + request.appointmentId()));
        if (appointment.getStatus() != AppointmentStatus.BOOKED && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalArgumentException("This appointment is not in a state that can be checked in.");
        }
        if (repository.existsByAppointmentId(appointment.getId())) {
            throw new IllegalArgumentException("This appointment has already been checked in.");
        }

        Consultant consultant = appointment.getConsultant();
        int token = tokenService.nextTokenNumber(consultant.getId(), appointment.getAppointmentDate());

        DoctorQueueEntry entry = new DoctorQueueEntry();
        entry.setConsultant(consultant);
        entry.setPatient(appointment.getPatient());
        entry.setAppointment(appointment);
        entry.setQueueDate(appointment.getAppointmentDate());
        entry.setTokenNumber(token);
        entry.setTokenDisplay(tokenDisplay(consultant, token));
        entry.setSource(DoctorQueueSource.APPOINTMENT);
        entry.setScheduledSlotTime(appointment.getSlotTime());
        entry.setStatus(DoctorQueueStatus.CHECKED_IN);
        entry.setCheckedInAt(Instant.now());
        DoctorQueueEntry checkedIn = repository.save(entry);
        recordAudit("CHECK_IN", null, checkedIn);

        DoctorQueueEntrySnapshot beforeWaiting = snapshot(checkedIn);
        checkedIn.setStatus(DoctorQueueStatus.WAITING);
        DoctorQueueEntry waiting = repository.save(checkedIn);
        recordAudit("ENTER_WAITING", beforeWaiting, waiting);

        return toDto(waiting);
    }

    @Transactional
    public DoctorQueueEntryDto registerWalkIn(WalkInRegistrationRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + request.patientId()));
        Consultant consultant = consultantRepository.findById(request.consultantId())
                .orElseThrow(() -> new EntityNotFoundException("Consultant not found: " + request.consultantId()));

        LocalDate today = LocalDate.now();
        int token = tokenService.nextTokenNumber(consultant.getId(), today);

        DoctorQueueEntry entry = new DoctorQueueEntry();
        entry.setConsultant(consultant);
        entry.setPatient(patient);
        entry.setAppointment(null);
        entry.setQueueDate(today);
        entry.setTokenNumber(token);
        entry.setTokenDisplay(tokenDisplay(consultant, token));
        entry.setSource(DoctorQueueSource.WALK_IN);
        entry.setScheduledSlotTime(null);
        entry.setStatus(DoctorQueueStatus.CHECKED_IN);
        entry.setCheckedInAt(Instant.now());
        entry.setPriority(request.priority() != null ? request.priority() : DoctorQueuePriority.NORMAL);
        entry.setPriorityReason(request.priorityReason());
        DoctorQueueEntry checkedIn = repository.save(entry);
        recordAudit("WALK_IN", null, checkedIn);

        DoctorQueueEntrySnapshot beforeWaiting = snapshot(checkedIn);
        checkedIn.setStatus(DoctorQueueStatus.WAITING);
        DoctorQueueEntry waiting = repository.save(checkedIn);
        recordAudit("ENTER_WAITING", beforeWaiting, waiting);

        return toDto(waiting);
    }

    @Transactional
    public DoctorQueueEntryDto nextPatient(Long consultantId, LocalDate date) {
        getConsultantOrThrow(consultantId);
        tokenService.lockDailyQueue(consultantId, date);

        if (repository
                .findFirstByConsultantIdAndQueueDateAndStatusIn(
                        consultantId, date, EnumSet.of(DoctorQueueStatus.CALLED, DoctorQueueStatus.IN_CONSULTATION))
                .isPresent()) {
            throw new IllegalArgumentException("A patient is already called - complete or skip them before calling the next patient.");
        }

        List<DoctorQueueEntry> waiting = repository.findWaitingOrderedByPriority(consultantId, date);
        if (waiting.isEmpty()) {
            throw new IllegalArgumentException("No patient is waiting to be called.");
        }

        DoctorQueueEntry entry = waiting.get(0);
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setStatus(DoctorQueueStatus.CALLED);
        entry.setCalledAt(Instant.now());
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("CALL_NEXT", before, saved);
        return toDto(saved);
    }

    @Transactional
    public DoctorQueueEntryDto recall(Long id) {
        DoctorQueueEntry entry = getOrThrow(id);
        if (entry.getStatus() != DoctorQueueStatus.CALLED) {
            throw new IllegalArgumentException("Entry is not currently called.");
        }
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setCalledAt(Instant.now());
        entry.setRecallCount(entry.getRecallCount() + 1);
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("RECALL", before, saved);
        return toDto(saved);
    }

    @Transactional
    public DoctorQueueEntryDto skip(Long id) {
        DoctorQueueEntry entry = getOrThrow(id);
        if (entry.getStatus() != DoctorQueueStatus.CALLED) {
            throw new IllegalArgumentException("Entry is not currently called.");
        }
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setStatus(DoctorQueueStatus.SKIPPED);
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("SKIP", before, saved);
        return toDto(saved);
    }

    @Transactional
    public DoctorQueueEntryDto complete(Long id) {
        DoctorQueueEntry entry = getOrThrow(id);
        if (entry.getStatus() != DoctorQueueStatus.CALLED) {
            throw new IllegalArgumentException("Entry is not currently called.");
        }
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setConsultationStartedAt(entry.getCalledAt());
        entry.setStatus(DoctorQueueStatus.IN_CONSULTATION);
        DoctorQueueEntry inConsultation = repository.save(entry);
        recordAudit("START_CONSULTATION", before, inConsultation);

        DoctorQueueEntrySnapshot mid = snapshot(inConsultation);
        inConsultation.setCompletedAt(Instant.now());
        inConsultation.setStatus(DoctorQueueStatus.COMPLETED);
        DoctorQueueEntry completed = repository.save(inConsultation);
        recordAudit("COMPLETE", mid, completed);

        ensureBillableAppointment(completed);

        return toDto(completed);
    }

    @Transactional
    public DoctorQueueEntryDto escalate(Long id, String reason) {
        DoctorQueueEntry entry = getOrThrow(id);
        if (entry.getStatus() != DoctorQueueStatus.WAITING && entry.getStatus() != DoctorQueueStatus.CALLED) {
            throw new IllegalArgumentException("Only a waiting or called entry can be escalated.");
        }
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setPriority(DoctorQueuePriority.EMERGENCY);
        entry.setPriorityReason(reason);
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("ESCALATE", before, saved);
        return toDto(saved);
    }

    @Transactional
    public DoctorQueueEntryDto deEscalate(Long id) {
        DoctorQueueEntry entry = getOrThrow(id);
        if (entry.getPriority() != DoctorQueuePriority.EMERGENCY
                || (entry.getStatus() != DoctorQueueStatus.WAITING && entry.getStatus() != DoctorQueueStatus.CALLED)) {
            throw new IllegalArgumentException("Entry is not currently an active emergency-priority entry.");
        }
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setPriority(DoctorQueuePriority.NORMAL);
        entry.setPriorityReason(null);
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("DE_ESCALATE", before, saved);
        return toDto(saved);
    }

    @Transactional
    public DoctorQueueEntryDto cancel(Long id, String reason) {
        DoctorQueueEntry entry = getOrThrow(id);
        if (!entry.getStatus().isActive()) {
            throw new IllegalArgumentException("Entry is already finished and cannot be cancelled.");
        }
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setStatus(DoctorQueueStatus.CANCELLED);
        entry.setReason(reason);
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("CANCEL", before, saved);
        return toDto(saved);
    }

    /** Not exposed via REST - called only from AppointmentService.cancel() so a cancelled appointment never leaves a ghost token in a doctor's live queue. */
    @Transactional
    public void cascadeCancelForAppointment(Long appointmentId, String reason) {
        repository.findByAppointmentId(appointmentId)
                .filter(entry -> entry.getStatus().isActive())
                .ifPresent(entry -> {
                    DoctorQueueEntrySnapshot before = snapshot(entry);
                    entry.setStatus(DoctorQueueStatus.CANCELLED);
                    entry.setReason("Appointment cancelled: " + reason);
                    DoctorQueueEntry saved = repository.save(entry);
                    recordAudit("CASCADE_CANCEL_FROM_APPOINTMENT", before, saved);
                });
    }

    @Transactional
    public DoctorQueueEntryDto moveBackToWaiting(Long id) {
        DoctorQueueEntry entry = getOrThrow(id);
        if (entry.getStatus() != DoctorQueueStatus.SKIPPED) {
            throw new IllegalArgumentException("Only a skipped entry can be moved back to waiting.");
        }
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setStatus(DoctorQueueStatus.WAITING);
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("MOVE_TO_WAITING", before, saved);
        return toDto(saved);
    }

    @Transactional
    public DoctorQueueEntryDto markNoShow(Long id, String reason) {
        DoctorQueueEntry entry = getOrThrow(id);
        if (entry.getStatus() != DoctorQueueStatus.WAITING && entry.getStatus() != DoctorQueueStatus.CALLED) {
            throw new IllegalArgumentException("Only a waiting or called entry can be marked no-show.");
        }
        DoctorQueueEntrySnapshot before = snapshot(entry);
        entry.setStatus(DoctorQueueStatus.NO_SHOW);
        entry.setReason(reason);
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("NO_SHOW", before, saved);
        return toDto(saved);
    }

    @Transactional
    public DoctorQueueEntryDto markNoShowForNeverCheckedIn(NoShowForAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId())
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + request.appointmentId()));
        if (appointment.getStatus() != AppointmentStatus.BOOKED && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalArgumentException("This appointment is not in a state that can be marked no-show.");
        }
        if (repository.existsByAppointmentId(appointment.getId())) {
            throw new IllegalArgumentException("This appointment already has a queue entry.");
        }

        Consultant consultant = appointment.getConsultant();
        int token = tokenService.nextTokenNumber(consultant.getId(), appointment.getAppointmentDate());

        DoctorQueueEntry entry = new DoctorQueueEntry();
        entry.setConsultant(consultant);
        entry.setPatient(appointment.getPatient());
        entry.setAppointment(appointment);
        entry.setQueueDate(appointment.getAppointmentDate());
        entry.setTokenNumber(token);
        entry.setTokenDisplay(tokenDisplay(consultant, token));
        entry.setSource(DoctorQueueSource.APPOINTMENT);
        entry.setScheduledSlotTime(appointment.getSlotTime());
        entry.setStatus(DoctorQueueStatus.NO_SHOW);
        entry.setCheckedInAt(Instant.now());
        entry.setReason(request.reason());
        DoctorQueueEntry saved = repository.save(entry);
        recordAudit("MARK_NO_SHOW_NO_CHECKIN", null, saved);
        return toDto(saved);
    }

    /**
     * A completed appointment-sourced entry already has a real Appointment
     * that's sat in BOOKED (the Appointment List's "Pending" tab, i.e. the
     * billing approval queue) since it was booked - nothing to do. A
     * completed walk-in has none (registerWalkIn() never creates one), so it
     * would otherwise have zero billing record anywhere in the system; this
     * synthesizes one now, reusing the exact same Pending-tab/bill() pipeline
     * every other OP bill goes through. Deliberately only called from
     * complete() - a walk-in that ends NO_SHOW/CANCELLED/never-un-skipped
     * must never get a billing-pending appointment.
     */
    private void ensureBillableAppointment(DoctorQueueEntry entry) {
        if (entry.getAppointment() != null) {
            return;
        }
        Appointment appointment = new Appointment();
        appointment.setPatient(entry.getPatient());
        appointment.setConsultant(entry.getConsultant());
        appointment.setAppointmentDate(entry.getQueueDate());
        appointment.setSlotTime(LocalTime.ofInstant(entry.getCheckedInAt(), ZoneId.systemDefault()));
        appointment.setNotes("Walk-in via Doctor Queue - token " + entry.getTokenDisplay());
        appointment.setStatus(AppointmentStatus.BOOKED);
        Appointment saved = appointmentRepository.save(appointment);

        entry.setAppointment(saved);
        repository.save(entry);

        recordAppointmentAudit(saved);
    }

    /** Mirrors AppointmentService.recordAudit()'s CREATE-operation shape for this system-generated appointment. */
    private void recordAppointmentAudit(Appointment appointment) {
        Patient patient = appointment.getPatient();
        Consultant consultant = appointment.getConsultant();
        AppointmentAuditLog log = new AppointmentAuditLog();
        log.setAppointment(appointment);
        log.setOperation("AUTO_CREATE_FROM_QUEUE");
        log.setPatientName(displayName(patient));
        log.setConsultantName(consultant.getName());
        log.setDepartmentName(consultant.getDepartment().getName());
        log.setAppointmentDate(appointment.getAppointmentDate());
        log.setSlotTime(appointment.getSlotTime());
        log.setChannel("queue");
        log.setPerformedBy(currentUsername());
        log.setPerformedAt(Instant.now());
        appointmentAuditLogRepository.save(log);
    }

    // ---------- Helpers ----------

    private String tokenDisplay(Consultant consultant, int token) {
        return consultant.getQueueTokenPrefix() + String.format("%03d", token);
    }

    private Consultant getConsultantOrThrow(Long consultantId) {
        return consultantRepository.findById(consultantId)
                .orElseThrow(() -> new EntityNotFoundException("Consultant not found: " + consultantId));
    }

    private DoctorQueueEntry getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Queue entry not found: " + id));
    }

    private ReceptionWorklistEntryDto toVirtualWorklistDto(Appointment appointment, Consultant consultant) {
        Patient patient = appointment.getPatient();
        Instant sortTimestamp = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getSlotTime())
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return new ReceptionWorklistEntryDto(
                null,
                appointment.getId(),
                patient.getId(),
                displayName(patient),
                patient.getRegistrationNumber(),
                patient.getAge(),
                patient.getGender(),
                consultant.getId(),
                consultant.getName(),
                "BOOKED",
                null,
                appointment.getSlotTime(),
                null,
                null,
                null,
                sortTimestamp);
    }

    private ReceptionWorklistEntryDto toWorklistDto(DoctorQueueEntry entry) {
        Patient patient = entry.getPatient();
        Consultant consultant = entry.getConsultant();
        return new ReceptionWorklistEntryDto(
                entry.getId(),
                entry.getAppointment() != null ? entry.getAppointment().getId() : null,
                patient.getId(),
                displayName(patient),
                patient.getRegistrationNumber(),
                patient.getAge(),
                patient.getGender(),
                consultant.getId(),
                consultant.getName(),
                entry.getStatus().name(),
                entry.getSource(),
                entry.getScheduledSlotTime(),
                entry.getTokenDisplay(),
                entry.getCheckedInAt(),
                entry.getPriority(),
                entry.getCheckedInAt());
    }

    private DoctorQueueEntryDto toDto(DoctorQueueEntry entry) {
        Patient patient = entry.getPatient();
        Consultant consultant = entry.getConsultant();
        return new DoctorQueueEntryDto(
                entry.getId(),
                consultant.getId(),
                consultant.getName(),
                patient.getId(),
                patient.getRegistrationNumber(),
                displayName(patient),
                patient.getAge(),
                patient.getGender(),
                entry.getAppointment() != null ? entry.getAppointment().getId() : null,
                entry.getQueueDate(),
                entry.getTokenNumber(),
                entry.getTokenDisplay(),
                entry.getSource(),
                entry.getScheduledSlotTime(),
                entry.getStatus(),
                entry.getPriority(),
                entry.getPriorityReason(),
                entry.getCheckedInAt(),
                entry.getCalledAt(),
                entry.getRecallCount(),
                entry.getConsultationStartedAt(),
                entry.getCompletedAt(),
                entry.getReason());
    }

    private DoctorQueueAuditLogDto toAuditDto(DoctorQueueAuditLog log) {
        return new DoctorQueueAuditLogDto(
                log.getId(),
                log.getOperation(),
                log.getPatientName(),
                log.getConsultantName(),
                log.getQueueDate(),
                log.getTokenDisplay(),
                log.getPreviousValue(),
                log.getNewValue(),
                log.getPerformedBy(),
                log.getPerformedAt());
    }

    private String displayName(Patient patient) {
        return (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
    }

    /** The subset of DoctorQueueEntry fields that actually change across the mutating operations above. */
    private record DoctorQueueEntrySnapshot(
            DoctorQueueStatus status,
            DoctorQueuePriority priority,
            String priorityReason,
            Instant calledAt,
            int recallCount,
            Instant consultationStartedAt,
            Instant completedAt,
            String reason) {
    }

    private DoctorQueueEntrySnapshot snapshot(DoctorQueueEntry entry) {
        return new DoctorQueueEntrySnapshot(
                entry.getStatus(),
                entry.getPriority(),
                entry.getPriorityReason(),
                entry.getCalledAt(),
                entry.getRecallCount(),
                entry.getConsultationStartedAt(),
                entry.getCompletedAt(),
                entry.getReason());
    }

    private void recordAudit(String operation, DoctorQueueEntrySnapshot before, DoctorQueueEntry after) {
        DoctorQueueAuditLog log = new DoctorQueueAuditLog();
        log.setQueueEntry(after);
        log.setOperation(operation);
        log.setPatientName(displayName(after.getPatient()));
        log.setConsultantName(after.getConsultant().getName());
        log.setQueueDate(after.getQueueDate());
        log.setTokenDisplay(after.getTokenDisplay());
        log.setPreviousValue(before != null ? toJson(before) : null);
        log.setNewValue(toJson(snapshot(after)));
        log.setPerformedBy(currentUsername());
        log.setPerformedAt(Instant.now());
        auditLogRepository.save(log);
    }

    private String toJson(DoctorQueueEntrySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            // Serialization failing shouldn't fail the queue operation it's
            // attached to - the log row just gets a null snapshot.
            return null;
        }
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
