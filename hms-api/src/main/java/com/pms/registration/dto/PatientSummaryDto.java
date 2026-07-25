package com.pms.registration.dto;

import com.pms.ipadmission.dto.AdmissionDto;
import com.pms.lab.dto.LabRequisitionDto;
import java.util.List;

/**
 * Backs the Patient Information (OP/IP) screen's single-round-trip
 * consolidated view - one read-only envelope around each domain's own
 * existing DTO (AdmissionDto, AppointmentDto, LabRequisitionDto,
 * PatientReportDto), reusing them as-is rather than duplicating their
 * fields. Only billing/payments get a new unifying shape
 * (BillingSummaryItem/PaymentSummaryItem), since Invoice/OpDirectBilling/IP
 * billing don't share a DTO today - see that record's own doc comment.
 *
 * "Cancelled Details" is deliberately not a separate field here - the
 * frontend derives it by filtering appointments/investigations/billing for
 * their own cancelled state, since each already carries that information.
 */
public record PatientSummaryDto(
        PatientDto patient,
        List<AdmissionDto> admissions,
        List<AppointmentDto> appointments,
        List<LabRequisitionDto> investigations,
        List<BillingSummaryItem> billing,
        List<PaymentSummaryItem> payments,
        List<PatientReportDto> files) {
}
