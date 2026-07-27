package com.pms.registration.service;

import com.pms.ipadmission.dto.AdmissionDto;
import com.pms.ipadmission.service.AdmissionService;
import com.pms.ipbilling.dto.IpPaymentDto;
import com.pms.ipbilling.service.IpBillingService;
import com.pms.lab.service.LabRequisitionService;
import com.pms.registration.dto.BillingSummaryItem;
import com.pms.registration.dto.BillingSummaryItem.BillingSource;
import com.pms.registration.dto.OpDirectBillingListEntryDto;
import com.pms.registration.dto.PatientDto;
import com.pms.registration.dto.PatientSummaryDto;
import com.pms.registration.dto.PaymentSummaryItem;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the Patient Information (OP/IP) screen's single-round-trip
 * consolidated view - reads through each domain's own existing service/
 * repository rather than owning any new business logic; this is a reporting
 * aggregation only, every write still goes through its own dedicated
 * service. Built incrementally per the Patient Information plan's step
 * order - fields land as each step's backend piece is done, starting with
 * patient + appointments here.
 *
 * Billing unifies two genuinely separate sources (OP Direct Billing, IP
 * Billing) into one BillingSummaryItem shape per source discriminator - see
 * the Patient Information plan's decision 2. IP billing
 * has no per-patient query of its own since it's admission-scoped, so it's
 * resolved from the patient's own admissions (already fetched above) rather
 * than a new repository method.
 */
@Service
@Transactional(readOnly = true)
public class PatientSummaryService {

    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final AdmissionService admissionService;
    private final LabRequisitionService labRequisitionService;
    private final OpDirectBillingService opDirectBillingService;
    private final IpBillingService ipBillingService;
    private final PatientReportService patientReportService;

    public PatientSummaryService(
            PatientService patientService,
            AppointmentService appointmentService,
            AdmissionService admissionService,
            LabRequisitionService labRequisitionService,
            OpDirectBillingService opDirectBillingService,
            IpBillingService ipBillingService,
            PatientReportService patientReportService) {
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.admissionService = admissionService;
        this.labRequisitionService = labRequisitionService;
        this.opDirectBillingService = opDirectBillingService;
        this.ipBillingService = ipBillingService;
        this.patientReportService = patientReportService;
    }

    public PatientSummaryDto summary(Long patientId) {
        PatientDto patient = patientService.findById(patientId);
        List<AdmissionDto> admissions = admissionService.findByPatientId(patientId);

        return new PatientSummaryDto(
                patient,
                admissions,
                appointmentService.search(null, null, null, null, patientId),
                labRequisitionService.findByPatientId(patientId),
                billing(patientId, admissions),
                payments(patientId, admissions),
                patientReportService.getActiveFiles(patientId));
    }

    private List<BillingSummaryItem> billing(Long patientId, List<AdmissionDto> admissions) {
        List<BillingSummaryItem> items = new ArrayList<>();

        for (OpDirectBillingListEntryDto billing : opDirectBillingService.findByPatientId(patientId)) {
            double total = billing.totalAmount() != null ? billing.totalAmount() : 0;
            boolean cancelled = billing.refundAmount() != null;
            items.add(new BillingSummaryItem(
                    BillingSource.OP_DIRECT_BILLING,
                    billing.id(),
                    billing.invoiceNumber() != null ? billing.invoiceNumber().toString() : null,
                    billing.billedAt() != null ? billing.billedAt().atZone(ZoneId.systemDefault()).toLocalDate() : null,
                    total,
                    total,
                    0.0,
                    0.0,
                    cancelled,
                    null,
                    !cancelled));
        }

        for (AdmissionDto admission : admissions) {
            if (admission.totalBilled() == null) {
                continue;
            }
            double due = admission.settlementAmount() != null && admission.settlementAmount() < 0 ? -admission.settlementAmount() : 0.0;
            items.add(new BillingSummaryItem(
                    BillingSource.IP_BILLING,
                    admission.id(),
                    admission.admissionNumber(),
                    admission.dischargeDate() != null ? admission.dischargeDate().toLocalDate() : null,
                    admission.totalBilled(),
                    admission.advanceAmount(),
                    0.0,
                    due,
                    false,
                    null,
                    false));
        }

        return items;
    }

    private List<PaymentSummaryItem> payments(Long patientId, List<AdmissionDto> admissions) {
        List<PaymentSummaryItem> items = new ArrayList<>();

        for (OpDirectBillingListEntryDto billing : opDirectBillingService.findByPatientId(patientId)) {
            items.add(new PaymentSummaryItem(
                    BillingSource.OP_DIRECT_BILLING,
                    billing.id(),
                    billing.invoiceNumber() != null ? billing.invoiceNumber().toString() : null,
                    billing.billedAt(),
                    billing.totalAmount(),
                    billing.paymentMode() != null ? billing.paymentMode().name() : null));
        }

        for (AdmissionDto admission : admissions) {
            for (IpPaymentDto payment : ipBillingService.listPayments(admission.id())) {
                items.add(new PaymentSummaryItem(
                        BillingSource.IP_BILLING,
                        admission.id(),
                        payment.receiptNumber(),
                        payment.paymentDate(),
                        payment.netAmount(),
                        payment.paymentType()));
            }
        }

        return items;
    }
}
