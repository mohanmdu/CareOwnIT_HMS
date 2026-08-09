package com.pms.registration.service;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.Consultant;
import com.pms.registration.dto.OpCaseSheetDto;
import com.pms.registration.dto.OpCaseSheetHeaderDto;
import com.pms.registration.dto.OpCaseSheetSaveRequest;
import com.pms.registration.dto.OpPrescriptionItemDto;
import com.pms.registration.dto.OpPrescriptionItemRequest;
import com.pms.registration.dto.PrescriptionWorklistEntryDto;
import com.pms.registration.dto.ReviewDateReportEntryDto;
import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.OpCaseSheet;
import com.pms.registration.entity.OpDirectBilling;
import com.pms.registration.entity.OpPrescriptionItem;
import com.pms.registration.entity.Patient;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.OpCaseSheetRepository;
import com.pms.registration.repository.OpDirectBillingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient Prescription module (migration doc's OP Case Sheet screens): one
 * case sheet per appointment, upserted wholesale (including its prescription
 * items) on every save - matches the legacy "Save draft" button's semantics
 * of always overwriting with the latest full form state.
 */
@Service
@Transactional(readOnly = true)
public class OpCaseSheetService {

    private final AppointmentRepository appointmentRepository;
    private final OpCaseSheetRepository caseSheetRepository;
    private final OpDirectBillingRepository opDirectBillingRepository;

    public OpCaseSheetService(
            AppointmentRepository appointmentRepository,
            OpCaseSheetRepository caseSheetRepository,
            OpDirectBillingRepository opDirectBillingRepository) {
        this.appointmentRepository = appointmentRepository;
        this.caseSheetRepository = caseSheetRepository;
        this.opDirectBillingRepository = opDirectBillingRepository;
    }

    /**
     * Merges two visit sources: appointments (which may need a case sheet
     * documented) and OP Direct Billing walk-ins (view-only here - a case
     * sheet requires a real appointment, and there's nothing clinical to
     * document for a walk-in charge; see the Patient Prescription plan).
     */
    public List<PrescriptionWorklistEntryDto> worklist(LocalDate fromDate, LocalDate toDate, Long consultantId, String search) {
        Stream<PrescriptionWorklistEntryDto> appointmentEntries =
                appointmentRepository.prescriptionWorklist(fromDate, toDate, consultantId, search).stream()
                        .map(this::toWorklistEntry);

        Instant fromInstant = fromDate != null ? fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Instant toInstant = toDate != null ? toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Stream<PrescriptionWorklistEntryDto> directBillingEntries =
                opDirectBillingRepository.prescriptionWorklist(fromInstant, toInstant, consultantId, search).stream()
                        .map(this::toWorklistEntry);

        return Stream.concat(appointmentEntries, directBillingEntries)
                .sorted(Comparator.comparing(PrescriptionWorklistEntryDto::appointmentDate)
                        .thenComparing(PrescriptionWorklistEntryDto::slotTime)
                        .reversed())
                .toList();
    }

    public OpCaseSheetDto getOrCreateShell(Long appointmentId) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        return caseSheetRepository
                .findByAppointmentId(appointmentId)
                .map(this::toDto)
                .orElseGet(() -> emptyShell(appointment));
    }

    @Transactional
    public OpCaseSheetDto save(Long appointmentId, OpCaseSheetSaveRequest request) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        OpCaseSheet caseSheet = caseSheetRepository.findByAppointmentId(appointmentId).orElseGet(OpCaseSheet::new);
        caseSheet.setAppointment(appointment);
        applyFields(caseSheet, request);

        caseSheet.getPrescriptionItems().clear();
        List<OpPrescriptionItemRequest> items = request.prescriptionItems() != null ? request.prescriptionItems() : List.of();
        int sortOrder = 0;
        for (OpPrescriptionItemRequest itemRequest : items) {
            OpPrescriptionItem item = new OpPrescriptionItem();
            item.setCaseSheet(caseSheet);
            item.setDrugName(itemRequest.drugName());
            item.setQty(itemRequest.qty());
            item.setIntake(itemRequest.intake());
            item.setMorningDose(itemRequest.morningDose());
            item.setAfternoonDose(itemRequest.afternoonDose());
            item.setEveningDose(itemRequest.eveningDose());
            item.setNightDose(itemRequest.nightDose());
            item.setSortOrder(sortOrder++);
            caseSheet.getPrescriptionItems().add(item);
        }

        return toDto(caseSheetRepository.save(caseSheet));
    }

    public List<ReviewDateReportEntryDto> reviewDateReport(LocalDate fromDate, LocalDate toDate, boolean upcoming) {
        return caseSheetRepository.reviewDateReport(fromDate, toDate, upcoming).stream()
                .map(this::toReviewDateEntry)
                .toList();
    }

    private void applyFields(OpCaseSheet caseSheet, OpCaseSheetSaveRequest request) {
        caseSheet.setFoodDrugAllergy(request.foodDrugAllergy());
        caseSheet.setHeightCm(request.heightCm());
        caseSheet.setWeightKg(request.weightKg());
        caseSheet.setBmi(request.bmi());
        caseSheet.setTemperatureF(request.temperatureF());
        caseSheet.setPulseBpm(request.pulseBpm());
        caseSheet.setRespirationBpm(request.respirationBpm());
        caseSheet.setBpSystolic(request.bpSystolic());
        caseSheet.setBpDiastolic(request.bpDiastolic());
        caseSheet.setSpo2(request.spo2());
        caseSheet.setBodyFatPercent(request.bodyFatPercent());
        caseSheet.setChiefComplaints(request.chiefComplaints());
        caseSheet.setPastMedicalCondition(request.pastMedicalCondition());
        caseSheet.setCurrentMedication(request.currentMedication());
        caseSheet.setPhysicalActivity(request.physicalActivity());
        caseSheet.setSleepDurationHours(request.sleepDurationHours());
        caseSheet.setSmoking(request.smoking());
        caseSheet.setAlcohol(request.alcohol());
        caseSheet.setSurgery(request.surgery());
        caseSheet.setFamilyHistory(request.familyHistory());
        caseSheet.setProvisionalDiagnosis(request.provisionalDiagnosis());
        caseSheet.setCbg(request.cbg());
        caseSheet.setFindings(request.findings());
        caseSheet.setInvestigation(request.investigation());
        caseSheet.setDoctorNotes1(request.doctorNotes1());
        caseSheet.setDoctorNotes2(request.doctorNotes2());
        caseSheet.setDiet(request.diet());
        caseSheet.setRemarks(request.remarks());
        caseSheet.setReviewDate(request.reviewDate());
    }

    private Appointment getAppointmentOrThrow(Long appointmentId) {
        return appointmentRepository
                .findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + appointmentId));
    }

    private OpCaseSheetDto emptyShell(Appointment appointment) {
        return new OpCaseSheetDto(
                null, // id
                toHeader(appointment),
                null, // foodDrugAllergy
                null, null, null, null, null, // heightCm, weightKg, bmi, temperatureF, pulseBpm
                null, null, null, null, null, // respirationBpm, bpSystolic, bpDiastolic, spo2, bodyFatPercent
                null, null, null, // chiefComplaints, pastMedicalCondition, currentMedication
                null, null, null, null, null, null, // physicalActivity, sleepDurationHours, smoking, alcohol, surgery, familyHistory
                null, null, null, null, null, null, // provisionalDiagnosis, cbg, findings, investigation, doctorNotes1, doctorNotes2
                List.of(), // prescriptionItems
                null, null, null, // diet, remarks, reviewDate
                null, null); // createdAt, updatedAt
    }

    private OpCaseSheetHeaderDto toHeader(Appointment appointment) {
        Patient patient = appointment.getPatient();
        Consultant consultant = appointment.getConsultant();
        return new OpCaseSheetHeaderDto(
                appointment.getId(),
                patient.getRegistrationNumber(),
                displayName(patient),
                patient.getGender(),
                patient.getAge(),
                patient.getMobileNumber(),
                appointment.getAppointmentDate(),
                appointment.getSlotTime(),
                consultant.getName());
    }

    private OpCaseSheetDto toDto(OpCaseSheet caseSheet) {
        List<OpPrescriptionItemDto> items = new ArrayList<>();
        for (OpPrescriptionItem item : caseSheet.getPrescriptionItems()) {
            items.add(new OpPrescriptionItemDto(
                    item.getId(),
                    item.getDrugName(),
                    item.getQty(),
                    item.getIntake(),
                    item.getMorningDose(),
                    item.getAfternoonDose(),
                    item.getEveningDose(),
                    item.getNightDose()));
        }
        return new OpCaseSheetDto(
                caseSheet.getId(),
                toHeader(caseSheet.getAppointment()),
                caseSheet.getFoodDrugAllergy(),
                caseSheet.getHeightCm(),
                caseSheet.getWeightKg(),
                caseSheet.getBmi(),
                caseSheet.getTemperatureF(),
                caseSheet.getPulseBpm(),
                caseSheet.getRespirationBpm(),
                caseSheet.getBpSystolic(),
                caseSheet.getBpDiastolic(),
                caseSheet.getSpo2(),
                caseSheet.getBodyFatPercent(),
                caseSheet.getChiefComplaints(),
                caseSheet.getPastMedicalCondition(),
                caseSheet.getCurrentMedication(),
                caseSheet.getPhysicalActivity(),
                caseSheet.getSleepDurationHours(),
                caseSheet.getSmoking(),
                caseSheet.getAlcohol(),
                caseSheet.getSurgery(),
                caseSheet.getFamilyHistory(),
                caseSheet.getProvisionalDiagnosis(),
                caseSheet.getCbg(),
                caseSheet.getFindings(),
                caseSheet.getInvestigation(),
                caseSheet.getDoctorNotes1(),
                caseSheet.getDoctorNotes2(),
                items,
                caseSheet.getDiet(),
                caseSheet.getRemarks(),
                caseSheet.getReviewDate(),
                caseSheet.getCreatedAt(),
                caseSheet.getUpdatedAt());
    }

    private PrescriptionWorklistEntryDto toWorklistEntry(Appointment appointment) {
        Patient patient = appointment.getPatient();
        Consultant consultant = appointment.getConsultant();
        boolean hasCaseSheet = caseSheetRepository.existsByAppointmentId(appointment.getId());
        LocalDate reviewDate = caseSheetRepository
                .findByAppointmentId(appointment.getId())
                .map(OpCaseSheet::getReviewDate)
                .orElse(null);
        return new PrescriptionWorklistEntryDto(
                appointment.getId(),
                null,
                "APPOINTMENT",
                patient.getId(),
                displayName(patient),
                patient.getRegistrationNumber(),
                patient.getAge(),
                patient.getGender(),
                patient.getMobileNumber(),
                consultant.getDepartment().getName(),
                consultant.getName(),
                appointment.getAppointmentDate(),
                appointment.getSlotTime(),
                hasCaseSheet,
                reviewDate);
    }

    private PrescriptionWorklistEntryDto toWorklistEntry(OpDirectBilling billing) {
        Patient patient = billing.getPatient();
        // resolveConsultant() falls back to the first line item's consultant -
        // the picker moved to per-item in the multi-item rebuild, so the bill's
        // own header-level getConsultant() is null on every bill saved since.
        Consultant consultant = billing.resolveConsultant();
        ZonedDateTime billedAt = billing.getBilledAt().atZone(ZoneId.systemDefault());
        return new PrescriptionWorklistEntryDto(
                null,
                billing.getId(),
                "DIRECT_BILLING",
                patient.getId(),
                displayName(patient),
                patient.getRegistrationNumber(),
                patient.getAge(),
                patient.getGender(),
                patient.getMobileNumber(),
                consultant != null && consultant.getDepartment() != null ? consultant.getDepartment().getName() : null,
                consultant != null ? consultant.getName() : null,
                billedAt.toLocalDate(),
                billedAt.toLocalTime(),
                false,
                null);
    }

    private ReviewDateReportEntryDto toReviewDateEntry(OpCaseSheet caseSheet) {
        Appointment appointment = caseSheet.getAppointment();
        Patient patient = appointment.getPatient();
        return new ReviewDateReportEntryDto(
                appointment.getId(),
                displayName(patient),
                patient.getRegistrationNumber(),
                patient.getAge(),
                patient.getGender(),
                patient.getMobileNumber(),
                appointment.getAppointmentDate(),
                appointment.getConsultant().getName(),
                caseSheet.getReviewDate());
    }

    private String displayName(Patient patient) {
        return (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
    }
}
