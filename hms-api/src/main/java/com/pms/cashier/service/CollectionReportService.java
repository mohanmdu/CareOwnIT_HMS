package com.pms.cashier.service;

import com.pms.cashier.dto.BillType;
import com.pms.cashier.dto.CollectionReportDto;
import com.pms.cashier.dto.CollectionReportRowDto;
import com.pms.cashier.dto.CollectionReportTotalsDto;
import com.pms.cashier.dto.CollectionReportUserSummaryDto;
import com.pms.cashier.dto.CollectionType;
import com.pms.ipbilling.entity.IpPayment;
import com.pms.ipbilling.repository.IpPaymentRepository;
import com.pms.lab.entity.LabPaymentMode;
import com.pms.lab.entity.LabRequisition;
import com.pms.lab.entity.LabRequisitionStatus;
import com.pms.lab.repository.LabRequisitionRepository;
import com.pms.masters.entity.Consultant;
import com.pms.masters.repository.GeneralUserRepository;
import com.pms.pharmacy.entity.PharmacyPaymentMode;
import com.pms.pharmacy.entity.PharmacySale;
import com.pms.pharmacy.entity.PharmacySaleSource;
import com.pms.pharmacy.repository.PharmacySaleRepository;
import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.OpDirectBilling;
import com.pms.registration.entity.Patient;
import com.pms.registration.entity.PaymentMode;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.OpDirectBillingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cashier Module's "Collection Report" - a single view over every real
 * money-collection event across the app (Appointment/consultation fees, OP
 * Direct Billing, Lab/Investigation charges, IP Advance/Final Settlement/Due
 * collections, Pharmacy bills). Pure read-through aggregator, same
 * philosophy as com.pms.registration.service.PatientSummaryService: zero new
 * business logic, every write still goes through its own dedicated service;
 * this only reads and reshapes.
 *
 * Date range is required (bounds the 5-source query); the remaining filters
 * are pushed to each source's own existing repository method where that
 * method already supports it, then finalized with a pass over the combined
 * in-memory list for the filters no single source's query can express
 * (username, cross-source paymentMode, department/consultant on sources with
 * no such concept).
 *
 * Built incrementally: Appointment + OP Direct Billing first (both always
 * Bill Type OP, the simplest two sources), Lab/Pharmacy/IP Payment land in
 * follow-up additions - see the Cashier Collection Report plan.
 */
@Service
@Transactional(readOnly = true)
public class CollectionReportService {

    private final AppointmentRepository appointmentRepository;
    private final OpDirectBillingRepository opDirectBillingRepository;
    private final LabRequisitionRepository labRequisitionRepository;
    private final PharmacySaleRepository pharmacySaleRepository;
    private final IpPaymentRepository ipPaymentRepository;
    private final GeneralUserRepository generalUserRepository;

    public CollectionReportService(
            AppointmentRepository appointmentRepository,
            OpDirectBillingRepository opDirectBillingRepository,
            LabRequisitionRepository labRequisitionRepository,
            PharmacySaleRepository pharmacySaleRepository,
            IpPaymentRepository ipPaymentRepository,
            GeneralUserRepository generalUserRepository) {
        this.appointmentRepository = appointmentRepository;
        this.opDirectBillingRepository = opDirectBillingRepository;
        this.labRequisitionRepository = labRequisitionRepository;
        this.pharmacySaleRepository = pharmacySaleRepository;
        this.ipPaymentRepository = ipPaymentRepository;
        this.generalUserRepository = generalUserRepository;
    }

    public CollectionReportDto search(
            LocalDate from,
            LocalDate to,
            BillType billType,
            CollectionType collectionType,
            String paymentMode,
            Long departmentId,
            Long consultantId,
            String username) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("From date and To date are required");
        }
        Instant fromInstant = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        Map<String, String> userDisplayNames = generalUserRepository.findAll().stream()
                .collect(LinkedHashMap::new, (map, u) -> map.put(u.getUsername(), u.getName()), Map::putAll);

        List<CollectionReportRowDto> rows = new ArrayList<>();

        // Appointment - always Bill Type OP.
        if (billType != BillType.IP && includes(collectionType, CollectionType.APPOINTMENT_FEE)) {
            PaymentMode appointmentPaymentMode = parseEnum(PaymentMode.class, paymentMode);
            for (Appointment appointment : appointmentRepository.collectionReport(
                    fromInstant, toInstant, consultantId, appointmentPaymentMode)) {
                rows.add(toRow(appointment, userDisplayNames));
            }
        }

        // OP Direct Billing - always Bill Type OP. Consultant/department are
        // optional on a walk-in charge - the consultantId/departmentId
        // filters below (line ~174) already re-narrow using the real values
        // toRow() now populates, so this block doesn't need its own guard.
        if (billType != BillType.IP && includes(collectionType, CollectionType.OP_DIRECT_BILLING)) {
            PaymentMode directPaymentMode = parseEnum(PaymentMode.class, paymentMode);
            for (OpDirectBilling billing :
                    opDirectBillingRepository.collectionReport(fromInstant, toInstant, consultantId, directPaymentMode)) {
                rows.add(toRow(billing, userDisplayNames));
            }
        }

        // Lab & Investigations - Bill Type derived per-row from LabRequisition.patientType.
        if (includes(collectionType, CollectionType.LAB_CHARGES) || includes(collectionType, CollectionType.INVESTIGATION_CHARGES)) {
            String requisitionType = collectionType == null
                    ? null
                    : switch (collectionType) {
                        case LAB_CHARGES -> "Labtest";
                        case INVESTIGATION_CHARGES -> "Billing";
                        default -> null;
                    };
            LabPaymentMode labPaymentMode = parseEnum(LabPaymentMode.class, paymentMode);
            for (LabRequisition requisition : labRequisitionRepository.findForCollectionReport(
                    LabRequisitionStatus.APPROVED, fromDateTime, toDateTime, requisitionType, consultantId, labPaymentMode, null)) {
                rows.add(toRow(requisition, userDisplayNames));
            }
        }

        // IP Payment - always Bill Type IP, no consultant/department FK concept at all (only
        // admission.primaryConsultant, a free-text field - see toRow(IpPayment, ...)).
        if (billType != BillType.OP
                && consultantId == null
                && departmentId == null
                && (includes(collectionType, CollectionType.IP_ADVANCE)
                        || includes(collectionType, CollectionType.IP_FINAL_SETTLEMENT)
                        || includes(collectionType, CollectionType.IP_DUE_COLLECTION))) {
            for (IpPayment payment : ipPaymentRepository.findByPaymentDateBetweenOrderByPaymentDateDesc(fromInstant, toInstant)) {
                CollectionReportRowDto row = toRow(payment, userDisplayNames);
                if (includes(collectionType, row.collectionType())) {
                    rows.add(row);
                }
            }
        }

        // Pharmacy - Bill Type NOT pushed to SQL: PharmacySaleSource.OTHERS folds into BillType.OP for
        // this report (see toRow(PharmacySale, ...)), so a single-value source param would wrongly
        // exclude OTHERS rows when billType=OP is requested - the in-memory billType filter below
        // handles this correctly instead.
        if (includes(collectionType, CollectionType.PHARMACY_BILL)) {
            PharmacyPaymentMode pharmacyPaymentMode = parseEnum(PharmacyPaymentMode.class, paymentMode);
            for (PharmacySale sale : pharmacySaleRepository.search(fromInstant, toInstant, null, pharmacyPaymentMode, null, username)) {
                rows.add(toRow(sale, userDisplayNames));
            }
        }

        List<CollectionReportRowDto> filtered = rows.stream()
                .filter(r -> billType == null || billType == r.billType())
                .filter(r -> username == null || username.isBlank() || username.equals(r.billedByUsername()))
                .filter(r -> paymentMode == null || paymentMode.isBlank() || paymentMode.equalsIgnoreCase(r.paymentMode()))
                .filter(r -> departmentId == null || departmentId.equals(r.departmentId()))
                .filter(r -> consultantId == null || consultantId.equals(r.consultantId()))
                .sorted(Comparator.comparing(CollectionReportRowDto::billedAt).reversed())
                .toList();

        return new CollectionReportDto(filtered, totals(filtered), userSummary(filtered));
    }

    private boolean includes(CollectionType filter, CollectionType candidate) {
        return filter == null || filter == candidate;
    }

    private CollectionReportRowDto toRow(Appointment appointment, Map<String, String> userDisplayNames) {
        Patient patient = appointment.getPatient();
        Consultant consultant = appointment.getConsultant();
        double invoiceAmount = consultant.getConsultationFee();
        double discount = appointment.getDiscountAmount() != null ? appointment.getDiscountAmount() : 0.0;
        double referral = appointment.getDoctorReferralAmount() != null ? appointment.getDoctorReferralAmount() : 0.0;
        double receipt = appointment.getPaidAmount() != null ? appointment.getPaidAmount() : 0.0;
        double refund = appointment.getRefundAmount() != null ? appointment.getRefundAmount() : 0.0;
        return new CollectionReportRowDto(
                CollectionType.APPOINTMENT_FEE,
                BillType.OP,
                appointment.getId(),
                patientDisplayName(patient),
                patient.getRegistrationNumber(),
                appointment.getBilledAt(),
                appointment.getInvoiceNumber() != null ? String.valueOf(appointment.getInvoiceNumber()) : null,
                appointment.getPaymentMode() != null ? appointment.getPaymentMode().name() : null,
                consultant.getName(),
                consultant.getDepartment() != null ? consultant.getDepartment().getName() : null,
                appointment.getBilledBy(),
                userDisplayNames.get(appointment.getBilledBy()),
                consultant.getDepartment() != null ? consultant.getDepartment().getId() : null,
                consultant.getId(),
                invoiceAmount,
                discount,
                referral,
                receipt,
                refund);
    }

    private CollectionReportRowDto toRow(OpDirectBilling billing, Map<String, String> userDisplayNames) {
        Patient patient = billing.getPatient();
        Consultant consultant = billing.resolveConsultant();
        double total = billing.getTotalAmount() != null ? billing.getTotalAmount() : 0.0;
        double refund = billing.getRefundAmount() != null ? billing.getRefundAmount() : 0.0;
        return new CollectionReportRowDto(
                CollectionType.OP_DIRECT_BILLING,
                BillType.OP,
                billing.getId(),
                patientDisplayName(patient),
                patient.getRegistrationNumber(),
                billing.getBilledAt(),
                billing.getInvoiceNumber() != null ? String.valueOf(billing.getInvoiceNumber()) : null,
                billing.getPaymentMode() != null ? billing.getPaymentMode().name() : null,
                consultant != null ? consultant.getName() : null,
                consultant != null && consultant.getDepartment() != null ? consultant.getDepartment().getName() : null,
                billing.getBilledBy(),
                userDisplayNames.get(billing.getBilledBy()),
                consultant != null && consultant.getDepartment() != null ? consultant.getDepartment().getId() : null,
                consultant != null ? consultant.getId() : null,
                total,
                0.0,
                0.0,
                total,
                refund);
    }

    private CollectionReportRowDto toRow(LabRequisition requisition, Map<String, String> userDisplayNames) {
        Patient patient = requisition.getPatient();
        Consultant consultant = requisition.getConsultant();
        CollectionType collectionType =
                "Billing".equals(requisition.getRequisitionType()) ? CollectionType.INVESTIGATION_CHARGES : CollectionType.LAB_CHARGES;
        BillType billType = "IP".equals(requisition.getPatientType()) ? BillType.IP : BillType.OP;
        double total = requisition.getTotalAmount();
        double discount = requisition.getDiscountAmount() != null ? requisition.getDiscountAmount() : 0.0;
        double paid = requisition.getPaidAmount() != null ? requisition.getPaidAmount() : 0.0;
        double refund = requisition.getRefundAmount() != null ? requisition.getRefundAmount() : 0.0;
        Instant billedAt = requisition.getApprovedAt() != null
                ? requisition.getApprovedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null;
        return new CollectionReportRowDto(
                collectionType,
                billType,
                requisition.getId(),
                patientDisplayName(patient),
                patient.getRegistrationNumber(),
                billedAt,
                requisition.getInvoiceNumber() != null ? String.valueOf(requisition.getInvoiceNumber()) : null,
                requisition.getPaymentMode() != null ? requisition.getPaymentMode().name() : null,
                consultant != null ? consultant.getName() : null,
                consultant != null && consultant.getDepartment() != null ? consultant.getDepartment().getName() : null,
                requisition.getApprovedBy(),
                userDisplayNames.get(requisition.getApprovedBy()),
                consultant != null && consultant.getDepartment() != null ? consultant.getDepartment().getId() : null,
                consultant != null ? consultant.getId() : null,
                total,
                discount,
                0.0,
                paid,
                refund);
    }

    private CollectionReportRowDto toRow(PharmacySale sale, Map<String, String> userDisplayNames) {
        Patient patient = sale.getPatient();
        Consultant consultant = sale.getConsultant();
        BillType billType = sale.getSource() == PharmacySaleSource.IP ? BillType.IP : BillType.OP;
        double total = sale.getTotalAmount() != null ? sale.getTotalAmount() : 0.0;
        double discount = sale.getDiscountAmount() != null ? sale.getDiscountAmount() : 0.0;
        double paid = sale.getAmountPaid() != null ? sale.getAmountPaid() : 0.0;
        return new CollectionReportRowDto(
                CollectionType.PHARMACY_BILL,
                billType,
                sale.getId(),
                patientDisplayName(patient),
                patient.getRegistrationNumber(),
                sale.getBilledAt(),
                String.valueOf(sale.getBillNumber()),
                sale.getPaymentMode() != null ? sale.getPaymentMode().name() : null,
                consultant != null ? consultant.getName() : null,
                consultant != null && consultant.getDepartment() != null ? consultant.getDepartment().getName() : null,
                sale.getBilledBy(),
                userDisplayNames.get(sale.getBilledBy()),
                consultant != null && consultant.getDepartment() != null ? consultant.getDepartment().getId() : null,
                consultant != null ? consultant.getId() : null,
                total,
                discount,
                0.0,
                paid,
                0.0);
    }

    private CollectionReportRowDto toRow(IpPayment payment, Map<String, String> userDisplayNames) {
        Patient patient = payment.getAdmission().getPatient();
        CollectionType collectionType =
                switch (payment.getDescription() != null ? payment.getDescription() : "") {
                    case "Final Settlement" -> CollectionType.IP_FINAL_SETTLEMENT;
                    case "Due Amount" -> CollectionType.IP_DUE_COLLECTION;
                    default -> CollectionType.IP_ADVANCE;
                };
        double invoiced = payment.getInvoicedAmount();
        double refund = payment.getRefundAmount();
        return new CollectionReportRowDto(
                collectionType,
                BillType.IP,
                payment.getId(),
                patientDisplayName(patient),
                patient.getRegistrationNumber(),
                payment.getPaymentDate(),
                payment.getReceiptNumber(),
                payment.getPaymentType(),
                payment.getAdmission().getPrimaryConsultant(),
                null,
                payment.getCreatedBy(),
                userDisplayNames.get(payment.getCreatedBy()),
                null,
                null,
                invoiced,
                0.0,
                0.0,
                invoiced,
                refund);
    }

    private CollectionReportTotalsDto totals(List<CollectionReportRowDto> rows) {
        double invoiceAmount = rows.stream().mapToDouble(CollectionReportRowDto::invoiceAmount).sum();
        double discountAmount = rows.stream().mapToDouble(CollectionReportRowDto::discountAmount).sum();
        double doctorReferralAmount = rows.stream().mapToDouble(CollectionReportRowDto::doctorReferralAmount).sum();
        double receiptAmount = rows.stream().mapToDouble(CollectionReportRowDto::receiptAmount).sum();
        double refundAmount = rows.stream().mapToDouble(CollectionReportRowDto::refundAmount).sum();
        return new CollectionReportTotalsDto(
                invoiceAmount, discountAmount, doctorReferralAmount, receiptAmount, refundAmount, receiptAmount - refundAmount);
    }

    private List<CollectionReportUserSummaryDto> userSummary(List<CollectionReportRowDto> rows) {
        Map<String, List<CollectionReportRowDto>> byUser = new LinkedHashMap<>();
        for (CollectionReportRowDto row : rows) {
            String key = row.billedByUsername() != null ? row.billedByUsername() : "";
            byUser.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        List<CollectionReportUserSummaryDto> summary = new ArrayList<>();
        for (Map.Entry<String, List<CollectionReportRowDto>> entry : byUser.entrySet()) {
            List<CollectionReportRowDto> userRows = entry.getValue();
            String username = entry.getKey().isBlank() ? null : entry.getKey();
            String displayName = userRows.get(0).billedByDisplayName();
            double invoiceAmount = userRows.stream().mapToDouble(CollectionReportRowDto::invoiceAmount).sum();
            double discountAmount = userRows.stream().mapToDouble(CollectionReportRowDto::discountAmount).sum();
            double receiptAmount = userRows.stream().mapToDouble(CollectionReportRowDto::receiptAmount).sum();
            double refundAmount = userRows.stream().mapToDouble(CollectionReportRowDto::refundAmount).sum();
            summary.add(new CollectionReportUserSummaryDto(
                    username, displayName, userRows.size(), invoiceAmount, discountAmount, receiptAmount, refundAmount, receiptAmount - refundAmount));
        }
        return summary.stream().sorted(Comparator.comparingDouble(CollectionReportUserSummaryDto::netCollectionAmount).reversed()).toList();
    }

    private String patientDisplayName(Patient patient) {
        return (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
