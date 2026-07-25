package com.pms.lab.service;

import com.pms.activitylog.service.ActivityLogEntry;
import com.pms.activitylog.service.ActivityLogService;
import com.pms.common.EntityNotFoundException;
import com.pms.ipadmission.entity.Admission;
import com.pms.ipadmission.entity.AdmissionStatus;
import com.pms.ipadmission.repository.AdmissionRepository;
import com.pms.lab.dto.LabCategoryTestGroupDto;
import com.pms.lab.dto.LabRequisitionAdHocItemDto;
import com.pms.lab.dto.LabRequisitionApproveDto;
import com.pms.lab.dto.LabRequisitionCreateDto;
import com.pms.lab.dto.LabRequisitionDto;
import com.pms.lab.dto.LabRequisitionItemDto;
import com.pms.lab.dto.LabRequisitionListRowDto;
import com.pms.lab.dto.LabTestOptionDto;
import com.pms.lab.entity.LabRequisition;
import com.pms.lab.entity.LabRequisitionItem;
import com.pms.lab.entity.LabRequisitionStatus;
import com.pms.lab.entity.LabSubCategory;
import com.pms.lab.repository.LabRequisitionRepository;
import com.pms.lab.repository.LabSubCategoryRepository;
import com.pms.masters.entity.Consultant;
import com.pms.masters.entity.OpBillingComponent;
import com.pms.masters.repository.ConsultantRepository;
import com.pms.masters.repository.OpBillingComponentRepository;
import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.Patient;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.PatientRepository;
import com.pms.registration.service.InvoiceNumberService;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lab Test requisition + billing approval workflow: create (pending) ->
 * worklist -> Approve (assigns an invoice number from the shared
 * InvoiceNumberService and records payment) or Cancel. Mirrors the
 * Appointment/OP Direct Billing two-stage shape rather than inventing a new one.
 */
@Service
@Transactional(readOnly = true)
public class LabRequisitionService {

    private final LabRequisitionRepository repository;
    private final LabSubCategoryRepository subCategoryRepository;
    private final PatientRepository patientRepository;
    private final ConsultantRepository consultantRepository;
    private final AppointmentRepository appointmentRepository;
    private final AdmissionRepository admissionRepository;
    private final InvoiceNumberService invoiceNumberService;
    private final ActivityLogService activityLogService;
    private final LabTestEntryService labTestEntryService;
    private final OpBillingComponentRepository opBillingComponentRepository;

    public LabRequisitionService(
            LabRequisitionRepository repository,
            LabSubCategoryRepository subCategoryRepository,
            PatientRepository patientRepository,
            ConsultantRepository consultantRepository,
            AppointmentRepository appointmentRepository,
            AdmissionRepository admissionRepository,
            InvoiceNumberService invoiceNumberService,
            ActivityLogService activityLogService,
            LabTestEntryService labTestEntryService,
            OpBillingComponentRepository opBillingComponentRepository) {
        this.repository = repository;
        this.subCategoryRepository = subCategoryRepository;
        this.patientRepository = patientRepository;
        this.consultantRepository = consultantRepository;
        this.appointmentRepository = appointmentRepository;
        this.admissionRepository = admissionRepository;
        this.invoiceNumberService = invoiceNumberService;
        this.activityLogService = activityLogService;
        this.labTestEntryService = labTestEntryService;
        this.opBillingComponentRepository = opBillingComponentRepository;
    }

    /** The requisition form's checkbox groups: every active Sub-Category, grouped by its Category. */
    public List<LabCategoryTestGroupDto> getTestCatalog() {
        List<LabSubCategory> subCategories = subCategoryRepository.findAllByOrderByOrderingNoAsc();
        Map<Long, LabCategoryTestGroupDto> byCategory = new LinkedHashMap<>();
        for (LabSubCategory subCategory : subCategories) {
            Long categoryId = subCategory.getCategory().getId();
            LabCategoryTestGroupDto group = byCategory.computeIfAbsent(
                    categoryId,
                    id -> new LabCategoryTestGroupDto(id, subCategory.getCategory().getName(), new java.util.ArrayList<>()));
            group.tests()
                    .add(new LabTestOptionDto(subCategory.getId(), subCategory.getName(), subCategory.getOpAmount(), subCategory.getIpAmount()));
        }
        return byCategory.values().stream()
                .sorted(Comparator.comparing(LabCategoryTestGroupDto::categoryName))
                .toList();
    }

    @Transactional
    public LabRequisitionDto create(LabRequisitionCreateDto dto) {
        List<Long> subCategoryIds = dto.subCategoryIds() != null ? dto.subCategoryIds() : List.of();
        List<LabRequisitionAdHocItemDto> adHocItems = dto.adHocItems() != null ? dto.adHocItems() : List.of();
        if (subCategoryIds.isEmpty() && adHocItems.isEmpty()) {
            throw new IllegalArgumentException("A requisition needs at least one test or billing line item");
        }

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + dto.patientId()));
        Consultant consultant = dto.consultantId() != null
                ? consultantRepository.findById(dto.consultantId())
                        .orElseThrow(() -> new EntityNotFoundException("Consultant not found: " + dto.consultantId()))
                : null;
        String patientType = computePatientType(patient.getId());

        LabRequisition requisition = new LabRequisition();
        requisition.setRequisitionNumber(nextRequisitionNumber());
        // "Billing" mirrors the Investigations flow's ad-hoc OP Billing Catalog items; "Labtest" is the Lab Sub-Category flow's default.
        requisition.setRequisitionType(adHocItems.isEmpty() ? "Labtest" : "Billing");
        requisition.setPatient(patient);
        requisition.setAdmission(resolveActiveAdmission(patient.getId()));
        requisition.setConsultant(consultant);
        requisition.setPatientType(patientType);
        requisition.setBillingType(dto.billingType());
        requisition.setStatus(LabRequisitionStatus.PENDING);
        requisition.setRequisitionDate(LocalDateTime.now());
        requisition.setCreatedBy(currentUsername());

        double total = 0;
        for (Long subCategoryId : subCategoryIds) {
            LabSubCategory subCategory = subCategoryRepository.findById(subCategoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Lab sub-category not found: " + subCategoryId));
            double amount = "IP".equals(patientType) ? subCategory.getIpAmount() : subCategory.getOpAmount();
            LabRequisitionItem item = new LabRequisitionItem();
            item.setRequisition(requisition);
            item.setSubCategory(subCategory);
            item.setCategoryName(subCategory.getCategory().getName());
            item.setSubCategoryName(subCategory.getName());
            item.setAmount(amount);
            requisition.getItems().add(item);
            total += amount;
        }
        for (LabRequisitionAdHocItemDto adHocItem : adHocItems) {
            OpBillingComponent component = opBillingComponentRepository.findById(adHocItem.componentId())
                    .orElseThrow(() -> new EntityNotFoundException("Billing component not found: " + adHocItem.componentId()));
            double discount = adHocItem.discount() != null ? adHocItem.discount() : 0;
            double amount = component.getAmount() * adHocItem.quantity() - discount;
            LabRequisitionItem item = new LabRequisitionItem();
            item.setRequisition(requisition);
            item.setCategoryName(component.getCategory().getName());
            item.setSubCategoryName(component.getName());
            item.setAmount(amount);
            requisition.getItems().add(item);
            total += amount;
        }
        requisition.setTotalAmount(total);

        LabRequisition saved = repository.save(requisition);
        activityLogService.log(new ActivityLogEntry("Laboratory", "Create")
                .content((adHocItems.isEmpty() ? "Lab requisition " : "Investigations billing advice ") + saved.getRequisitionNumber() + " for " + total)
                .status("Pending")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .screenName(adHocItems.isEmpty() ? "Lab Requisition" : "Patient Billing Advice"));
        return toDto(saved);
    }

    public List<LabRequisitionListRowDto> getPendingList() {
        return repository.findByStatusOrderByRequisitionDateDesc(LabRequisitionStatus.PENDING).stream()
                .map(this::toListRow)
                .toList();
    }

    public LabRequisitionDto getById(Long id) {
        return toDto(getOrThrow(id));
    }

    // Patient Information (OP/IP) screen's Investigations Done tab.
    public List<LabRequisitionDto> findByPatientId(Long patientId) {
        return repository.findByPatientIdOrderByRequisitionDateDesc(patientId).stream().map(this::toDto).toList();
    }

    @Transactional
    public LabRequisitionDto approve(Long id, LabRequisitionApproveDto dto) {
        LabRequisition requisition = getOrThrow(id);
        if (requisition.getStatus() != LabRequisitionStatus.PENDING) {
            throw new IllegalArgumentException("Requisition " + requisition.getRequisitionNumber() + " is no longer pending");
        }
        requisition.setInvoiceNumber(invoiceNumberService.next());
        requisition.setPaidAmount(dto.paidAmount());
        requisition.setDiscountAmount(dto.discountAmount() != null ? dto.discountAmount() : 0);
        requisition.setPaymentMode(dto.paymentMode());
        requisition.setRemarks(dto.remarks());
        requisition.setStatus(LabRequisitionStatus.APPROVED);
        requisition.setApprovedAt(LocalDateTime.now());
        requisition.setApprovedBy(currentUsername());

        LabRequisition saved = repository.save(requisition);
        labTestEntryService.createFromRequisition(saved);
        Patient patient = saved.getPatient();
        activityLogService.log(new ActivityLogEntry("Laboratory", "Payment Received")
                .content("Invoice " + saved.getInvoiceNumber() + " - " + saved.getPaidAmount())
                .status("Approved")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .screenName("Lab & X-Ray/Scan Billing"));
        return toDto(saved);
    }

    @Transactional
    public void cancel(Long id) {
        LabRequisition requisition = getOrThrow(id);
        if (requisition.getStatus() != LabRequisitionStatus.PENDING) {
            throw new IllegalArgumentException("Requisition " + requisition.getRequisitionNumber() + " is no longer pending");
        }
        requisition.setStatus(LabRequisitionStatus.CANCELLED);
        requisition.setCancelledAt(LocalDateTime.now());
        requisition.setCancelledBy(currentUsername());
        LabRequisition saved = repository.save(requisition);
        Patient patient = saved.getPatient();
        activityLogService.log(new ActivityLogEntry("Laboratory", "Cancel")
                .content("Lab requisition " + saved.getRequisitionNumber() + " cancelled")
                .status("Cancelled")
                .patient(patient.getRegistrationNumber(), patientDisplayName(patient))
                .screenName("Lab & X-Ray/Scan Billing"));
    }

    /** The patient's currently-admitted admission, if any - see LabRequisition.admission's Javadoc. */
    private Admission resolveActiveAdmission(Long patientId) {
        return admissionRepository
                .findFirstByPatientIdAndStatusOrderByAdmissionDateDesc(patientId, AdmissionStatus.ADMITTED)
                .orElse(null);
    }

    private String computePatientType(Long patientId) {
        var lastAppointment = appointmentRepository.findFirstByPatientIdOrderByAppointmentDateDescSlotTimeDesc(patientId);
        var lastAdmission = admissionRepository.findFirstByPatientIdOrderByAdmissionDateDesc(patientId);
        var appointmentDate = lastAppointment.map(Appointment::getAppointmentDate).orElse(null);
        var admissionDate = lastAdmission.map(a -> a.getAdmissionDate().toLocalDate()).orElse(null);
        boolean useAdmission = admissionDate != null && (appointmentDate == null || !admissionDate.isBefore(appointmentDate));
        return useAdmission ? "IP" : "OP";
    }

    private String nextRequisitionNumber() {
        String prefix = "LABREQ" + Year.now().getValue();
        return repository.findTopByRequisitionNumberStartingWithOrderByRequisitionNumberDesc(prefix)
                .map(r -> {
                    int next = Integer.parseInt(r.getRequisitionNumber().substring(prefix.length())) + 1;
                    return prefix + String.format("%05d", next);
                })
                .orElse(prefix + "00001");
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private String patientDisplayName(Patient patient) {
        return (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
    }

    private LabRequisition getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Lab requisition not found: " + id));
    }

    private LabRequisitionListRowDto toListRow(LabRequisition r) {
        Patient patient = r.getPatient();
        return new LabRequisitionListRowDto(
                r.getId(),
                patient.getRegistrationNumber(),
                patientDisplayName(patient),
                r.getPatientType(),
                r.getRequisitionType(),
                r.getRequisitionDate(),
                r.getTotalAmount(),
                r.getCreatedBy());
    }

    private LabRequisitionDto toDto(LabRequisition r) {
        Patient patient = r.getPatient();
        Consultant consultant = r.getConsultant();
        List<LabRequisitionItemDto> items = r.getItems().stream()
                .map(i -> new LabRequisitionItemDto(
                        i.getId(),
                        i.getSubCategory() != null ? i.getSubCategory().getId() : null,
                        i.getCategoryName(),
                        i.getSubCategoryName(),
                        i.getAmount()))
                .toList();
        return new LabRequisitionDto(
                r.getId(),
                r.getRequisitionNumber(),
                r.getRequisitionType(),
                patient.getId(),
                patient.getRegistrationNumber(),
                patientDisplayName(patient),
                patient.getAge(),
                patient.getGender(),
                "Normal",
                r.getPatientType(),
                r.getBillingType().name(),
                consultant != null ? consultant.getId() : null,
                consultant != null ? consultant.getName() : null,
                r.getTotalAmount(),
                r.getStatus().name(),
                r.getRequisitionDate(),
                r.getCreatedBy(),
                r.getInvoiceNumber(),
                r.getPaidAmount(),
                r.getDiscountAmount(),
                r.getPaymentMode() != null ? r.getPaymentMode().name() : null,
                r.getRemarks(),
                items);
    }
}
