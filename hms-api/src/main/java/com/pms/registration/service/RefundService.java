package com.pms.registration.service;

import com.pms.common.EntityNotFoundException;
import com.pms.registration.dto.RefundCandidateDto;
import com.pms.registration.dto.RefundReceiptEntryDto;
import com.pms.registration.dto.RefundRequest;
import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.OpDirectBilling;
import com.pms.registration.entity.Patient;
import com.pms.registration.entity.Refund;
import com.pms.registration.entity.RefundSource;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.OpDirectBillingRepository;
import com.pms.registration.repository.RefundRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment Refund module (migration doc's Doctor Appointment Module refund
 * screens): search a billed invoice, approve a single refund against it,
 * print a receipt, and report on refunds - mirroring the AppointmentService
 * billing flow one layer up. Works against either billing source
 * (Appointment or OP Direct Billing - they share one invoice-number
 * sequence, so an invoice number resolves to at most one of them). A refund
 * doesn't change the source's status; it only stamps its refundAmount so
 * the existing Collection Report total (paidAmount - refundAmount) already
 * reflects it for both sources.
 */
@Service
@Transactional(readOnly = true)
public class RefundService {

    private final AppointmentRepository appointmentRepository;
    private final OpDirectBillingRepository opDirectBillingRepository;
    private final RefundRepository refundRepository;

    public RefundService(
            AppointmentRepository appointmentRepository,
            OpDirectBillingRepository opDirectBillingRepository,
            RefundRepository refundRepository) {
        this.appointmentRepository = appointmentRepository;
        this.opDirectBillingRepository = opDirectBillingRepository;
        this.refundRepository = refundRepository;
    }

    public RefundCandidateDto searchByInvoiceNumber(Long invoiceNumber) {
        var appointment = appointmentRepository.findByInvoiceNumber(invoiceNumber);
        if (appointment.isPresent()) {
            return toCandidate(appointment.get());
        }
        var directBilling = opDirectBillingRepository.findByInvoiceNumber(invoiceNumber);
        if (directBilling.isPresent()) {
            return toCandidate(directBilling.get());
        }
        throw new EntityNotFoundException("No billed invoice found for Invoice No " + invoiceNumber);
    }

    @Transactional
    public RefundReceiptEntryDto create(RefundRequest request) {
        Refund refund = new Refund();
        Double paidAmount;
        if (request.source() == RefundSource.APPOINTMENT) {
            Appointment appointment = appointmentRepository.findById(request.sourceId())
                    .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + request.sourceId()));
            if (appointment.getInvoiceNumber() == null) {
                throw new IllegalArgumentException("This appointment hasn't been billed yet.");
            }
            if (refundRepository.existsByAppointmentId(appointment.getId())) {
                throw new IllegalArgumentException(
                        "Invoice No " + appointment.getInvoiceNumber() + " has already been refunded.");
            }
            paidAmount = appointment.getPaidAmount();
            validateAmount(request.refundAmount(), paidAmount);
            refund.setAppointment(appointment);
            appointment.setRefundAmount(request.refundAmount());
            appointmentRepository.save(appointment);
        } else {
            OpDirectBilling billing = opDirectBillingRepository.findById(request.sourceId())
                    .orElseThrow(() -> new EntityNotFoundException("OP Direct Billing not found: " + request.sourceId()));
            if (refundRepository.existsByOpDirectBillingId(billing.getId())) {
                throw new IllegalArgumentException(
                        "Invoice No " + billing.getInvoiceNumber() + " has already been refunded.");
            }
            paidAmount = billing.getTotalAmount();
            validateAmount(request.refundAmount(), paidAmount);
            refund.setOpDirectBilling(billing);
            billing.setRefundAmount(request.refundAmount());
            opDirectBillingRepository.save(billing);
        }

        refund.setRefundNumber(refundRepository.findTopByOrderByRefundNumberDesc().map(r -> r.getRefundNumber() + 1).orElse(1L));
        refund.setRefundAmount(request.refundAmount());
        refund.setReason(request.reason());
        refund.setRefundedBy(currentUsername());
        refund.setRefundedAt(Instant.now());
        return toReceiptEntry(refundRepository.save(refund));
    }

    public List<RefundReceiptEntryDto> report(LocalDate fromDate, LocalDate toDate, Long consultantId) {
        Instant fromInstant = fromDate != null ? fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Instant toInstant =
                toDate != null ? toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        return refundRepository.report(fromInstant, toInstant, consultantId).stream()
                .map(this::toReceiptEntry)
                .toList();
    }

    private void validateAmount(double refundAmount, Double paidAmount) {
        double paid = paidAmount != null ? paidAmount : 0.0;
        if (refundAmount <= 0 || refundAmount > paid) {
            throw new IllegalArgumentException("Refund amount must be greater than 0 and no more than the paid amount.");
        }
    }

    private RefundCandidateDto toCandidate(Appointment appointment) {
        Refund existing = refundRepository.findByAppointmentId(appointment.getId()).orElse(null);
        Patient patient = appointment.getPatient();
        return new RefundCandidateDto(
                appointment.getId(),
                RefundSource.APPOINTMENT,
                displayName(patient),
                patient.getRegistrationNumber(),
                appointment.getConsultant().getName(),
                appointment.getInvoiceNumber(),
                appointment.getConsultant().getConsultationFee(),
                appointment.getPaidAmount(),
                appointment.getBilledBy(),
                appointment.getBilledAt(),
                existing != null,
                existing != null ? existing.getRefundNumber() : null,
                existing != null ? existing.getRefundAmount() : null,
                existing != null ? existing.getRefundedAt() : null);
    }

    private RefundCandidateDto toCandidate(OpDirectBilling billing) {
        Refund existing = refundRepository.findByOpDirectBillingId(billing.getId()).orElse(null);
        Patient patient = billing.getPatient();
        return new RefundCandidateDto(
                billing.getId(),
                RefundSource.DIRECT_BILLING,
                displayName(patient),
                patient.getRegistrationNumber(),
                billing.getConsultant() != null ? billing.getConsultant().getName() : null,
                billing.getInvoiceNumber(),
                billing.getTotalAmount(),
                billing.getTotalAmount(),
                billing.getBilledBy(),
                billing.getBilledAt(),
                existing != null,
                existing != null ? existing.getRefundNumber() : null,
                existing != null ? existing.getRefundAmount() : null,
                existing != null ? existing.getRefundedAt() : null);
    }

    private RefundReceiptEntryDto toReceiptEntry(Refund refund) {
        if (refund.getAppointment() != null) {
            Appointment appointment = refund.getAppointment();
            Patient patient = appointment.getPatient();
            return new RefundReceiptEntryDto(
                    appointment.getId(),
                    RefundSource.APPOINTMENT,
                    refund.getRefundNumber(),
                    displayName(patient),
                    patient.getRegistrationNumber(),
                    appointment.getConsultant().getName(),
                    "Consultation",
                    appointment.getInvoiceNumber(),
                    appointment.getConsultant().getConsultationFee(),
                    appointment.getPaidAmount(),
                    refund.getRefundAmount(),
                    refund.getReason(),
                    appointment.getBilledAt(),
                    refund.getRefundedAt(),
                    refund.getRefundedBy());
        }
        OpDirectBilling billing = refund.getOpDirectBilling();
        Patient patient = billing.getPatient();
        return new RefundReceiptEntryDto(
                billing.getId(),
                RefundSource.DIRECT_BILLING,
                refund.getRefundNumber(),
                displayName(patient),
                patient.getRegistrationNumber(),
                billing.getConsultant() != null ? billing.getConsultant().getName() : null,
                "Direct Billing",
                billing.getInvoiceNumber(),
                billing.getTotalAmount(),
                billing.getTotalAmount(),
                refund.getRefundAmount(),
                refund.getReason(),
                billing.getBilledAt(),
                refund.getRefundedAt(),
                refund.getRefundedBy());
    }

    private String displayName(Patient patient) {
        return (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
