package com.pms.cashier.service;

import com.pms.activitylog.service.ActivityLogEntry;
import com.pms.activitylog.service.ActivityLogService;
import com.pms.cashier.dto.AdvanceReportRowDto;
import com.pms.cashier.dto.CancellationRequestRowDto;
import com.pms.cashier.dto.IpPaymentRequestDto;
import com.pms.cashier.entity.IpPaymentRequest;
import com.pms.cashier.entity.PaymentRequestStatus;
import com.pms.cashier.entity.PaymentRequestType;
import com.pms.cashier.repository.IpPaymentRequestRepository;
import com.pms.common.EntityNotFoundException;
import com.pms.ipadmission.entity.Admission;
import com.pms.ipadmission.repository.AdmissionRepository;
import com.pms.ipadmission.service.AdmissionService;
import com.pms.ipbilling.entity.IpPayment;
import com.pms.ipbilling.repository.IpPaymentRepository;
import com.pms.registration.entity.Patient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cashier Approval Workflow (PDF): a patient-side request ("Amount Received"
 * on the billing page) queues here PENDING until a cashier picks a payment
 * mode and approves it, which records the actual IpPayment ledger row.
 */
@Service
@Transactional(readOnly = true)
public class IpPaymentRequestService {

    private static final String[] REQUEST_TYPE_LABELS = {"Advance", "Final Settlement", "Due Amount"};

    private final IpPaymentRequestRepository repository;
    private final AdmissionRepository admissionRepository;
    private final AdmissionService admissionService;
    private final IpPaymentRepository paymentRepository;
    private final ActivityLogService activityLogService;

    public IpPaymentRequestService(
            IpPaymentRequestRepository repository,
            AdmissionRepository admissionRepository,
            AdmissionService admissionService,
            IpPaymentRepository paymentRepository,
            ActivityLogService activityLogService) {
        this.repository = repository;
        this.admissionRepository = admissionRepository;
        this.admissionService = admissionService;
        this.paymentRepository = paymentRepository;
        this.activityLogService = activityLogService;
    }

    public List<IpPaymentRequestDto> listPending() {
        return repository.findByStatusOrderByRequestedAtDesc(PaymentRequestStatus.PENDING).stream()
                .map(this::toDto)
                .toList();
    }

    public long countPending() {
        return repository.countByStatus(PaymentRequestStatus.PENDING);
    }

    public IpPaymentRequestDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public IpPaymentRequestDto create(Long admissionId, PaymentRequestType requestType, double amount, String description) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new EntityNotFoundException("Admission not found: " + admissionId));

        IpPaymentRequest request = new IpPaymentRequest();
        request.setAdmission(admission);
        request.setRequestType(requestType);
        request.setAmount(amount);
        request.setDescription(description);
        request.setStatus(PaymentRequestStatus.PENDING);
        request.setRequestedAt(Instant.now());
        request.setRequestedBy(currentUsername());

        IpPaymentRequest saved = repository.save(request);
        Patient patient = admission.getPatient();
        activityLogService.log(new ActivityLogEntry("Advance Payment", "Create")
                .content(requestTypeLabel(requestType) + " request for " + amount + (description != null && !description.isBlank() ? " (" + description + ")" : ""))
                .status("Pending")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .ipNumber(admission.getAdmissionNumber())
                .screenName("Patient IP Details"));
        return toDto(saved);
    }

    @Transactional
    public IpPaymentRequestDto approve(Long id, String paymentMode) {
        IpPaymentRequest request = getOrThrow(id);
        if (request.getStatus() != PaymentRequestStatus.PENDING) {
            throw new IllegalArgumentException("Payment request has already been processed");
        }

        IpPayment payment = admissionService.recordCashierPayment(
                request.getAdmission().getId(), request.getAmount(), requestTypeLabel(request.getRequestType()), paymentMode);

        request.setStatus(PaymentRequestStatus.APPROVED);
        request.setPaymentMode(paymentMode);
        request.setApprovedAt(Instant.now());
        request.setApprovedBy(currentUsername());
        request.setIpPayment(payment);

        IpPaymentRequest saved = repository.save(request);
        Admission admission = request.getAdmission();
        Patient patient = admission.getPatient();
        activityLogService.log(new ActivityLogEntry("Advance Payment", "Payment Received")
                .content(request.getAmount() + " received via " + paymentMode + " (Receipt " + payment.getReceiptNumber() + ")")
                .status("Approved")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .ipNumber(admission.getAdmissionNumber())
                .screenName("Cashier Approval"));
        return toDto(saved);
    }

    /**
     * Records a payment that's approved on the spot rather than routed through
     * the pending-request queue (e.g. an advance collected directly at Ward
     * Allocation or via the Admission Worklist's "Add Advance" action) - same
     * ledger write approve() does, wrapped in an already-APPROVED
     * IpPaymentRequest so it appears in Advance Report/Advance Cancel exactly
     * like a cashier-approved request would.
     */
    @Transactional
    public IpPaymentRequestDto createPreApproved(
            Long admissionId, PaymentRequestType requestType, double amount, String description, String paymentMode) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new EntityNotFoundException("Admission not found: " + admissionId));

        IpPayment payment = admissionService.recordCashierPayment(admissionId, amount, description, paymentMode);

        IpPaymentRequest request = new IpPaymentRequest();
        request.setAdmission(admission);
        request.setRequestType(requestType);
        request.setAmount(amount);
        request.setDescription(description);
        request.setStatus(PaymentRequestStatus.APPROVED);
        request.setRequestedAt(Instant.now());
        request.setRequestedBy(currentUsername());
        request.setPaymentMode(paymentMode);
        request.setApprovedAt(Instant.now());
        request.setApprovedBy(currentUsername());
        request.setIpPayment(payment);

        IpPaymentRequest saved = repository.save(request);
        Patient patient = admission.getPatient();
        activityLogService.log(new ActivityLogEntry("Advance Payment", "Payment Received")
                .content(requestTypeLabel(requestType) + " " + amount + " collected via " + paymentMode
                        + " (Receipt " + payment.getReceiptNumber() + ")")
                .status("Approved")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .ipNumber(admission.getAdmissionNumber())
                .screenName("Ward Allocation"));
        return toDto(saved);
    }

    /** Advance Report: every approved cashier request in a date range, one row per request. */
    public List<AdvanceReportRowDto> getAdvanceReport(LocalDate fromDate, LocalDate toDate) {
        Instant fromInstant = fromDate != null ? fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Instant toInstant = toDate != null ? toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        return repository.findApprovedForReport(fromInstant, toInstant).stream().map(this::toAdvanceReportRow).toList();
    }

    private AdvanceReportRowDto toAdvanceReportRow(IpPaymentRequest request) {
        Admission admission = request.getAdmission();
        Patient patient = admission.getPatient();
        double advance = request.getRequestType() == PaymentRequestType.ADVANCE ? request.getAmount() : 0;
        double finalSettlement = request.getRequestType() == PaymentRequestType.FINAL_SETTLEMENT ? request.getAmount() : 0;
        double dueAmount = request.getRequestType() == PaymentRequestType.DUE_AMOUNT ? request.getAmount() : 0;
        return new AdvanceReportRowDto(
                patient.getRegistrationNumber(),
                (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim(),
                patient.getGender(),
                admission.getAdmissionNumber(),
                request.getApprovedBy(),
                request.getApprovedAt(),
                advance,
                finalSettlement,
                dueAmount,
                request.getAmount());
    }

    /** Advance Cancel: every still-reversible approved request for a patient, searched by UHID across all their admissions. */
    public List<CancellationRequestRowDto> searchCancellableByUhid(String uhid) {
        return repository.findApprovedByPatientUhid(uhid).stream().map(this::toCancellationRow).toList();
    }

    /**
     * Reverses an approved cashier request (Cancel and Convert to Credit both
     * call this - only the reason text differs): marks the linked IpPayment
     * fully refunded and undoes its effect on the admission's advance amount.
     */
    @Transactional
    public IpPaymentRequestDto cancel(Long id, String reason) {
        IpPaymentRequest request = getOrThrow(id);
        if (request.getStatus() != PaymentRequestStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved requests can be cancelled");
        }

        IpPayment payment = request.getIpPayment();
        if (payment != null) {
            payment.setRefundAmount(payment.getInvoicedAmount());
            payment.setNetAmount(payment.getInvoicedAmount() - payment.getRefundAmount());
            paymentRepository.save(payment);
        }
        admissionService.reverseCashierPayment(request.getAdmission().getId(), request.getAmount());

        request.setStatus(PaymentRequestStatus.CANCELLED);
        request.setCancelReason(reason);
        request.setCancelledAt(Instant.now());
        request.setCancelledBy(currentUsername());

        IpPaymentRequest saved = repository.save(request);
        Admission admission = request.getAdmission();
        Patient patient = admission.getPatient();
        activityLogService.log(new ActivityLogEntry("Refund", "Refund Processed")
                .content("Refunded " + request.getAmount() + ". Reason: " + reason)
                .status("Cancelled")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .ipNumber(admission.getAdmissionNumber())
                .screenName("Advance Cancel"));
        return toDto(saved);
    }

    private String patientDisplayName(Patient patient) {
        return (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
    }

    private CancellationRequestRowDto toCancellationRow(IpPaymentRequest request) {
        Admission admission = request.getAdmission();
        Patient patient = admission.getPatient();
        IpPayment payment = request.getIpPayment();
        return new CancellationRequestRowDto(
                request.getId(),
                patient.getRegistrationNumber(),
                (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim(),
                admission.getAdmissionNumber(),
                request.getAmount(),
                request.getPaymentMode(),
                request.getApprovedAt(),
                requestTypeLabel(request.getRequestType()),
                "Pending",
                payment != null ? payment.getReceiptNumber() : null);
    }

    private String requestTypeLabel(PaymentRequestType type) {
        return REQUEST_TYPE_LABELS[type.ordinal()];
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private IpPaymentRequest getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Payment request not found: " + id));
    }

    private IpPaymentRequestDto toDto(IpPaymentRequest request) {
        Admission admission = request.getAdmission();
        Patient patient = admission.getPatient();
        IpPayment payment = request.getIpPayment();
        return new IpPaymentRequestDto(
                request.getId(),
                admission.getId(),
                admission.getAdmissionNumber(),
                patient.getRegistrationNumber(),
                (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim(),
                patient.getGender(),
                patient.getAge(),
                admission.getHeightCm(),
                admission.getWeightKg(),
                patient.getAddress(),
                admission.getPrimaryConsultant(),
                request.getRequestType(),
                request.getAmount(),
                request.getDescription(),
                request.getStatus(),
                request.getPaymentMode(),
                request.getRequestedAt(),
                request.getRequestedBy(),
                request.getApprovedAt(),
                request.getApprovedBy(),
                payment != null ? payment.getId() : null,
                payment != null ? payment.getReceiptNumber() : null);
    }
}
