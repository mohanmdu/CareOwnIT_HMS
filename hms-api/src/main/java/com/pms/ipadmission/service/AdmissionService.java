package com.pms.ipadmission.service;

import com.pms.activitylog.service.ActivityLogEntry;
import com.pms.activitylog.service.ActivityLogService;
import com.pms.common.EntityNotFoundException;
import com.pms.common.FileStorageService;
import com.pms.ipadmission.dto.AdmissionDto;
import com.pms.ipadmission.entity.Admission;
import com.pms.ipadmission.entity.AdmissionRoomHistory;
import com.pms.ipadmission.entity.AdmissionStatus;
import com.pms.ipadmission.entity.Room;
import com.pms.ipadmission.entity.RoomStatus;
import com.pms.ipadmission.entity.RoomType;
import com.pms.ipadmission.repository.AdmissionRepository;
import com.pms.ipadmission.repository.AdmissionRoomHistoryRepository;
import com.pms.ipadmission.repository.RoomRepository;
import com.pms.ipadmission.repository.RoomTypeRepository;
import com.pms.insurance.service.PreAuthorizationRequestService;
import com.pms.ipbilling.entity.IpPayment;
import com.pms.ipbilling.repository.IpPaymentRepository;
import com.pms.registration.entity.Patient;
import com.pms.registration.repository.PatientRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Replaces IPAction.createIPAdmission / patientAdvanceRequest(Confirm) /
 * IpDischargeInputBydate / finalizeDischarge / getDetailForIPRefund /
 * balanceRefundUpdate (migration doc §4.2, risk R9). The legacy
 * finalizeDischarge ran raw string-concatenated HQL bulk updates with a
 * race-prone max()-based ID sequence; this uses parameterized JPA
 * operations and a signed settlementAmount instead of separate
 * refund/balance-due code paths.
 *
 * register() is the two-step flow's Step 1 (Casualty/Emergency Admission
 * batch): captures intake details and a room *type* preference only, no room
 * assigned yet. admitRegistered() is Step 2 (Ward Allocation): assigns a bed
 * to that REGISTERED admission and flips it to ADMITTED. admit() remains a
 * separate direct single-step path (room required upfront) still used by the
 * AdmissionWorklistComponent's "New Admission" quick-admit section.
 */
@Service
@Transactional(readOnly = true)
public class AdmissionService {

    private final AdmissionRepository repository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final FileStorageService fileStorageService;
    private final IpPaymentRepository paymentRepository;
    private final AdmissionRoomHistoryRepository roomHistoryRepository;
    private final ActivityLogService activityLogService;
    private final PreAuthorizationRequestService preAuthorizationRequestService;
    private final AtomicInteger sequence = new AtomicInteger(0);
    private final AtomicInteger receiptSequence = new AtomicInteger(0);
    private final AtomicInteger dischargeSequence = new AtomicInteger(0);

    public AdmissionService(
            AdmissionRepository repository,
            PatientRepository patientRepository,
            RoomRepository roomRepository,
            RoomTypeRepository roomTypeRepository,
            FileStorageService fileStorageService,
            IpPaymentRepository paymentRepository,
            AdmissionRoomHistoryRepository roomHistoryRepository,
            ActivityLogService activityLogService,
            PreAuthorizationRequestService preAuthorizationRequestService) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.fileStorageService = fileStorageService;
        this.paymentRepository = paymentRepository;
        this.roomHistoryRepository = roomHistoryRepository;
        this.activityLogService = activityLogService;
        this.preAuthorizationRequestService = preAuthorizationRequestService;
        this.sequence.set((int) repository.count());
        this.receiptSequence.set((int) paymentRepository.count());
        this.dischargeSequence.set((int) repository.findAll().stream().filter(a -> a.getDischargeNumber() != null).count());
    }

    public List<AdmissionDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public AdmissionDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    // Patient Information (OP/IP) screen's Inpatient Details tab.
    public List<AdmissionDto> findByPatientId(Long patientId) {
        return repository.findByPatientIdOrderByAdmissionDateDesc(patientId).stream().map(this::toDto).toList();
    }

    @Transactional
    public AdmissionDto admit(AdmissionDto dto) {
        if (dto.roomId() == null) {
            throw new IllegalArgumentException("Room is required to admit a patient directly");
        }
        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + dto.patientId()));
        Room room = roomRepository.findById(dto.roomId())
                .orElseThrow(() -> new EntityNotFoundException("Room not found: " + dto.roomId()));
        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new IllegalArgumentException("Room " + room.getRoomNumber() + " is not available");
        }

        Admission admission = new Admission();
        admission.setAdmissionNumber(nextAdmissionNumber());
        admission.setPatient(patient);
        admission.setRoom(room);
        admission.setAdmissionDate(LocalDateTime.now());
        admission.setStatus(AdmissionStatus.ADMITTED);
        admission.setAdvanceAmount(0);
        admission.setCreatedBy(currentUsername());

        room.setStatus(RoomStatus.ALLOCATED);
        roomRepository.save(room);

        Admission saved = repository.save(admission);
        openRoomHistoryPeriod(saved, room, saved.getAdmissionDate());
        activityLogService.log(new ActivityLogEntry("Admission", "Create")
                .content("Admitted directly to Room " + room.getRoomNumber() + " (" + room.getRoomType().getName() + ")")
                .status("Success")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .ipNumber(saved.getAdmissionNumber())
                .screenName("New Admission"));
        return toDto(saved);
    }

    /**
     * Step 2 of the two-step flow (Ward Allocation): lets staff review/correct
     * the intake details captured at registration, assigns a bed, and flips
     * the admission to ADMITTED - mirrors the legacy "Edit Admission Advice +
     * Ward Allocation" screen. Reuses applyIntakeFields() so this and
     * register() stay in sync field-for-field. Any initial advance amount is
     * recorded separately by AdmissionController via
     * IpPaymentRequestService.createPreApproved(), so it also lands in the
     * cashier's Advance Report/Advance Cancel screens, not just this admission's own total.
     */
    @Transactional
    public AdmissionDto admitRegistered(Long id, AdmissionDto dto) {
        Admission admission = getOrThrow(id);
        if (admission.getStatus() != AdmissionStatus.REGISTERED) {
            throw new IllegalArgumentException(
                    "Admission " + admission.getAdmissionNumber() + " is not pending registration");
        }
        if (dto.roomId() == null) {
            throw new IllegalArgumentException("Room is required to admit a patient");
        }
        Room room = roomRepository.findById(dto.roomId())
                .orElseThrow(() -> new EntityNotFoundException("Room not found: " + dto.roomId()));
        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new IllegalArgumentException("Room " + room.getRoomNumber() + " is not available");
        }

        applyIntakeFields(admission, dto);
        if (dto.admissionDate() != null) {
            admission.setAdmissionDate(dto.admissionDate());
        }
        admission.setRoom(room);
        admission.setStatus(AdmissionStatus.ADMITTED);

        room.setStatus(RoomStatus.ALLOCATED);
        roomRepository.save(room);

        Admission saved = repository.save(admission);
        openRoomHistoryPeriod(saved, room, saved.getAdmissionDate());
        activityLogService.log(new ActivityLogEntry("Admission", "Update")
                .content("Ward Allocation: assigned Room " + room.getRoomNumber() + " (" + room.getRoomType().getName() + ")")
                .status("Success")
                .patient(saved.getPatient().getRegistrationNumber(), patientDisplayName(saved.getPatient()))
                .ipNumber(saved.getAdmissionNumber())
                .screenName("Ward Allocation"));
        return toDto(saved);
    }

    /**
     * Reopens an already-admitted admission's intake details for correction
     * (Inpatient Admission Details edit form) - reuses applyIntakeFields() so
     * it stays in sync with register()/admitRegistered(). Deliberately does
     * NOT touch room/bed assignment - that stays exclusively in the dedicated
     * Ward Change flow (changeRoom()) to avoid bypassing its availability checks.
     */
    @Transactional
    public AdmissionDto updateIntake(Long id, AdmissionDto dto) {
        Admission admission = getOrThrow(id);
        applyIntakeFields(admission, dto);
        Admission saved = repository.save(admission);
        activityLogService.log(new ActivityLogEntry("Admission", "Update")
                .content("Inpatient Admission Details updated")
                .status("Success")
                .patient(saved.getPatient().getRegistrationNumber(), patientDisplayName(saved.getPatient()))
                .ipNumber(saved.getAdmissionNumber())
                .screenName("Inpatient Admission Details"));
        return toDto(saved);
    }

    /** Step 1 of the Casualty/Emergency Admission flow: intake + room-type preference, no bed assigned yet. */
    @Transactional
    public AdmissionDto register(AdmissionDto dto) {
        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + dto.patientId()));

        Admission admission = new Admission();
        admission.setAdmissionNumber(nextAdmissionNumber());
        admission.setPatient(patient);
        admission.setRoomType(resolveRoomType(dto.roomTypeId()));
        admission.setAdmissionDate(dto.admissionDate() != null ? dto.admissionDate() : LocalDateTime.now());
        admission.setStatus(AdmissionStatus.REGISTERED);
        admission.setAdvanceAmount(0);
        admission.setCreatedBy(currentUsername());
        applyIntakeFields(admission, dto);

        Admission saved = repository.save(admission);
        preAuthorizationRequestService.seedFromAdmission(saved);
        activityLogService.log(new ActivityLogEntry("Admission", "Create")
                .content("Registered" + (admission.getRoomType() != null ? " for " + admission.getRoomType().getName() : ""))
                .status("Success")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .ipNumber(saved.getAdmissionNumber())
                .screenName("Casualty/Emergency Admission"));
        return toDto(saved);
    }

    @Transactional
    public AdmissionDto uploadPhoto(Long id, MultipartFile file) {
        Admission admission = getOrThrow(id);
        String photoPath = fileStorageService.store(file, "admissions");
        admission.setPhotoPath(photoPath);
        return toDto(repository.save(admission));
    }

    /**
     * Collects a payment (Advance / Final Settlement / Due Amount request
     * types all share this mechanism - only the ledger label and payment
     * mode differ), whether it's cashier-approved via IpPaymentRequestService
     * or recorded directly (see IpPaymentRequestService.createPreApproved()).
     * Also accepts DISCHARGE_INITIATED admissions since a Final Settlement is
     * typically collected between Initiate Discharge and Finalize Discharge.
     */
    @Transactional
    public IpPayment recordCashierPayment(Long id, double amount, String description, String paymentMode) {
        Admission admission = getOrThrow(id);
        requirePayable(admission);
        admission.setAdvanceAmount(admission.getAdvanceAmount() + amount);
        Admission saved = repository.save(admission);

        IpPayment payment = new IpPayment();
        payment.setAdmission(saved);
        payment.setPaymentDate(Instant.now());
        payment.setReceiptNumber(nextReceiptNumber());
        payment.setDescription(description);
        payment.setPaymentType(paymentMode);
        payment.setInvoicedAmount(amount);
        payment.setNetAmount(amount);
        payment.setCreatedBy(currentUsername());
        return paymentRepository.save(payment);
    }

    /**
     * Advance Cancel: undoes a previously-collected cashier payment (Cancel /
     * Convert to Credit both reverse the same way, only the reason differs).
     * No status guard here - unlike collecting a new payment, reversing one
     * is an administrative correction that must work regardless of what the
     * admission's current lifecycle state is (even after discharge).
     */
    @Transactional
    public void reverseCashierPayment(Long id, double amount) {
        Admission admission = getOrThrow(id);
        admission.setAdvanceAmount(admission.getAdvanceAmount() - amount);
        repository.save(admission);
    }

    private String nextReceiptNumber() {
        return "RCPT" + String.format("%06d", receiptSequence.incrementAndGet());
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    /**
     * Stage 1 of discharge (PDF p.13): sets the discharge date/type and assigns
     * the discharge number - room stays occupied, nothing settled yet. The
     * discharge number is issued here (not at Finalize) because the legacy
     * screen already displays it while "Finalize Discharge" is still pending.
     */
    @Transactional
    public AdmissionDto initiateDischarge(Long id, LocalDateTime dischargeDate, String dischargeType) {
        Admission admission = getOrThrow(id);
        requireAdmitted(admission);

        admission.setStatus(AdmissionStatus.DISCHARGE_INITIATED);
        admission.setDischargeDate(dischargeDate != null ? dischargeDate : LocalDateTime.now());
        admission.setDischargeType(dischargeType);
        admission.setDischargeNumber(nextDischargeNumber());

        Admission saved = repository.save(admission);
        activityLogService.log(new ActivityLogEntry("Discharge", "Update")
                .content("Discharge initiated (" + dischargeType + "), Discharge No: " + saved.getDischargeNumber())
                .status("Pending")
                .patient(saved.getPatient().getRegistrationNumber(), patientDisplayName(saved.getPatient()))
                .ipNumber(saved.getAdmissionNumber())
                .screenName("Initiate Discharge"));
        return toDto(saved);
    }

    /** Stage 2 of discharge: settles the balance, frees the room, and transitions straight to DISCHARGED. */
    @Transactional
    public AdmissionDto finalizeDischarge(Long id, double totalBilled, String dischargeSummary) {
        Admission admission = getOrThrow(id);
        if (admission.getStatus() != AdmissionStatus.DISCHARGE_INITIATED) {
            throw new IllegalArgumentException(
                    "Admission " + admission.getAdmissionNumber() + " has not initiated discharge yet");
        }

        admission.setStatus(AdmissionStatus.DISCHARGED);
        admission.setDischargeSummary(dischargeSummary);
        admission.setTotalBilled(totalBilled);
        // Positive = refund owed to patient; negative = balance due from patient.
        admission.setSettlementAmount(admission.getAdvanceAmount() - totalBilled);

        Room room = admission.getRoom();
        if (room != null) {
            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);
        }

        Admission saved = repository.save(admission);
        activityLogService.log(new ActivityLogEntry("Discharge", "Discharge Completed")
                .content("Total Billed: " + totalBilled + ", Settlement: " + saved.getSettlementAmount())
                .status("Success")
                .patient(saved.getPatient().getRegistrationNumber(), patientDisplayName(saved.getPatient()))
                .ipNumber(saved.getAdmissionNumber())
                .screenName("Finalize Discharge"));
        return toDto(saved);
    }

    private String nextDischargeNumber() {
        return "DIS" + String.format("%06d", dischargeSequence.incrementAndGet());
    }

    /**
     * Ward Change (Inpatient List "Ward Change" action): moves an ADMITTED
     * patient to a different available room, closing out the current
     * AdmissionRoomHistory period and opening a new one at changedAt so
     * IpBillingService can price each room the patient actually occupied.
     */
    @Transactional
    public AdmissionDto changeRoom(Long id, Long newRoomId, LocalDateTime changedAt) {
        Admission admission = getOrThrow(id);
        requireAdmitted(admission);
        if (newRoomId == null) {
            throw new IllegalArgumentException("A room is required to change ward");
        }

        Room newRoom = roomRepository.findById(newRoomId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found: " + newRoomId));
        if (newRoom.getStatus() != RoomStatus.AVAILABLE) {
            throw new IllegalArgumentException("Room " + newRoom.getRoomNumber() + " is not available");
        }

        Room oldRoom = admission.getRoom();
        if (oldRoom != null && oldRoom.getId().equals(newRoom.getId())) {
            throw new IllegalArgumentException("Patient is already in room " + newRoom.getRoomNumber());
        }

        LocalDateTime effectiveChangeTime = changedAt != null ? changedAt : LocalDateTime.now();

        Optional<AdmissionRoomHistory> openPeriod = roomHistoryRepository.findByAdmissionIdAndToDateIsNull(id);
        if (openPeriod.isPresent()) {
            if (effectiveChangeTime.isBefore(openPeriod.get().getFromDate())) {
                throw new IllegalArgumentException("Ward change date cannot be before the current room's start date");
            }
            openPeriod.get().setToDate(effectiveChangeTime);
            roomHistoryRepository.save(openPeriod.get());
        }

        if (oldRoom != null) {
            oldRoom.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(oldRoom);
        }

        admission.setRoom(newRoom);
        newRoom.setStatus(RoomStatus.ALLOCATED);
        roomRepository.save(newRoom);

        Admission saved = repository.save(admission);
        openRoomHistoryPeriod(saved, newRoom, effectiveChangeTime);
        activityLogService.log(new ActivityLogEntry("Admission", "Update")
                .content("Ward changed to Room " + newRoom.getRoomNumber() + " (" + newRoom.getRoomType().getName() + ")")
                .previousContent(oldRoom != null ? "Was in Room " + oldRoom.getRoomNumber() : "No prior room")
                .status("Updated")
                .patient(saved.getPatient().getRegistrationNumber(), patientDisplayName(saved.getPatient()))
                .ipNumber(saved.getAdmissionNumber())
                .screenName("Ward Change"));
        return toDto(saved);
    }

    /**
     * Cancelled Admissions (PDF audit screen): voids a REGISTERED or ADMITTED
     * admission before it reaches discharge. Frees the room (if one was
     * assigned) and closes out its open AdmissionRoomHistory period exactly
     * like a discharge would, but transitions straight to CANCELLED instead.
     */
    @Transactional
    public AdmissionDto cancelAdmission(Long id, String reason) {
        Admission admission = getOrThrow(id);
        if (admission.getStatus() != AdmissionStatus.REGISTERED && admission.getStatus() != AdmissionStatus.ADMITTED) {
            throw new IllegalArgumentException(
                    "Admission " + admission.getAdmissionNumber() + " can no longer be cancelled in its current state");
        }

        LocalDateTime cancelledAt = LocalDateTime.now();
        Room room = admission.getRoom();
        if (room != null) {
            Optional<AdmissionRoomHistory> openPeriod = roomHistoryRepository.findByAdmissionIdAndToDateIsNull(id);
            openPeriod.ifPresent(period -> {
                period.setToDate(cancelledAt);
                roomHistoryRepository.save(period);
            });
            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);
        }

        admission.setStatus(AdmissionStatus.CANCELLED);
        admission.setCancelledAt(cancelledAt);
        admission.setCancelledBy(currentUsername());
        admission.setCancellationReason(reason);

        Admission saved = repository.save(admission);
        activityLogService.log(new ActivityLogEntry("Admission", "Cancel")
                .content("Reason: " + reason)
                .status("Cancelled")
                .patient(saved.getPatient().getRegistrationNumber(), patientDisplayName(saved.getPatient()))
                .ipNumber(saved.getAdmissionNumber())
                .screenName("Cancelled Admissions"));
        return toDto(saved);
    }

    private void openRoomHistoryPeriod(Admission admission, Room room, LocalDateTime fromDate) {
        AdmissionRoomHistory period = new AdmissionRoomHistory();
        period.setAdmission(admission);
        period.setRoom(room);
        period.setFromDate(fromDate);
        roomHistoryRepository.save(period);
    }

    private String patientDisplayName(Patient patient) {
        return (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
    }

    private void applyIntakeFields(Admission admission, AdmissionDto dto) {
        admission.setAttenderName(dto.attenderName());
        admission.setRelationType(dto.relationType());
        admission.setFatherSpouseName(dto.fatherSpouseName());
        admission.setRelationMobileNo(dto.relationMobileNo());
        admission.setOccupation(dto.occupation());
        admission.setMaritalStatus(dto.maritalStatus());
        admission.setPeriodOfStayDays(dto.periodOfStayDays());
        admission.setDescriptionOfCase(dto.descriptionOfCase());
        admission.setReferralDoctor(dto.referralDoctor());
        admission.setPrimaryConsultant(dto.primaryConsultant());
        admission.setSecondaryConsultant(dto.secondaryConsultant());
        admission.setPaymentType(dto.paymentType());
        admission.setHeightCm(dto.heightCm());
        admission.setWeightKg(dto.weightKg());
        admission.setMlc(dto.mlc() != null && dto.mlc());
        admission.setInsuranceType(dto.insuranceType());
        admission.setCorporateName(dto.corporateName());
        admission.setTpaName(dto.tpaName());
        admission.setInsuranceCompany(dto.insuranceCompany());
        admission.setPatientType(dto.patientType());
        admission.setRemarks(dto.remarks());
        admission.setAadhaarNumber(dto.aadhaarNumber());
        admission.setVentilatorRequired(dto.ventilatorRequired() != null && dto.ventilatorRequired());
        admission.setMonitorRequired(dto.monitorRequired() != null && dto.monitorRequired());
    }

    private RoomType resolveRoomType(Long roomTypeId) {
        if (roomTypeId == null) {
            return null;
        }
        return roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new EntityNotFoundException("Room type not found: " + roomTypeId));
    }

    private void requireAdmitted(Admission admission) {
        if (admission.getStatus() != AdmissionStatus.ADMITTED) {
            throw new IllegalArgumentException("Admission " + admission.getAdmissionNumber() + " is already discharged");
        }
    }

    /** Allows payments both while ADMITTED and while a discharge is initiated (Final Settlement window). */
    private void requirePayable(Admission admission) {
        AdmissionStatus status = admission.getStatus();
        if (status != AdmissionStatus.ADMITTED && status != AdmissionStatus.DISCHARGE_INITIATED) {
            throw new IllegalArgumentException(
                    "Admission " + admission.getAdmissionNumber() + " cannot accept payments in its current state");
        }
    }

    private String nextAdmissionNumber() {
        return "IP" + String.format("%06d", sequence.incrementAndGet());
    }

    private Admission getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Admission not found: " + id));
    }

    private AdmissionDto toDto(Admission admission) {
        Patient patient = admission.getPatient();
        Room room = admission.getRoom();
        RoomType roomType = admission.getRoomType();
        return new AdmissionDto(
                admission.getId(),
                admission.getAdmissionNumber(),
                patient.getId(),
                patientDisplayName(patient),
                patient.getRegistrationNumber(),
                patient.getGender(),
                patient.getAge(),
                patient.getMobileNumber(),
                patient.getAddress(),
                room != null ? room.getId() : null,
                room != null ? room.getRoomNumber() : null,
                roomType != null ? roomType.getId() : (room != null ? room.getRoomType().getId() : null),
                roomType != null ? roomType.getName() : (room != null ? room.getRoomType().getName() : null),
                admission.getAdmissionDate(),
                admission.getStatus(),
                admission.getAdvanceAmount(),
                admission.getTotalBilled(),
                admission.getSettlementAmount(),
                admission.getDischargeDate(),
                admission.getDischargeSummary(),
                admission.getDischargeType(),
                admission.getDischargeNumber(),
                admission.getAttenderName(),
                admission.getRelationType(),
                admission.getFatherSpouseName(),
                admission.getRelationMobileNo(),
                admission.getOccupation(),
                admission.getMaritalStatus(),
                admission.getPeriodOfStayDays(),
                admission.getDescriptionOfCase(),
                admission.getReferralDoctor(),
                admission.getPrimaryConsultant(),
                admission.getSecondaryConsultant(),
                admission.getPaymentType(),
                admission.getHeightCm(),
                admission.getWeightKg(),
                admission.isMlc(),
                admission.getInsuranceType(),
                admission.getCorporateName(),
                admission.getTpaName(),
                admission.getInsuranceCompany(),
                admission.getPatientType(),
                admission.getRemarks(),
                admission.getAadhaarNumber(),
                admission.isVentilatorRequired(),
                admission.isMonitorRequired(),
                admission.getPhotoPath(),
                admission.getCreatedBy(),
                admission.getCancelledAt(),
                admission.getCancelledBy(),
                admission.getCancellationReason());
    }
}
