package com.pms.registration.service;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.Consultant;
import com.pms.masters.entity.OpBillingComponent;
import com.pms.masters.repository.ConsultantRepository;
import com.pms.masters.repository.OpBillingComponentRepository;
import com.pms.registration.dto.OpDirectBillingItemDto;
import com.pms.registration.dto.OpDirectBillingItemRequest;
import com.pms.registration.dto.OpDirectBillingListEntryDto;
import com.pms.registration.dto.OpDirectBillingReceiptDto;
import com.pms.registration.dto.OpDirectBillingRequest;
import com.pms.registration.entity.OpDirectBilling;
import com.pms.registration.entity.OpDirectBillingItem;
import com.pms.registration.entity.Patient;
import com.pms.registration.repository.OpDirectBillingRepository;
import com.pms.registration.repository.PatientRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OP Direct Billing (migration doc's walk-in billing module): bills
 * immediately on creation - there's no doctor/slot to approve, unlike
 * Appointment billing, so there's no separate pending state.
 */
@Service
@Transactional(readOnly = true)
public class OpDirectBillingService {

    private final OpDirectBillingRepository repository;
    private final PatientRepository patientRepository;
    private final ConsultantRepository consultantRepository;
    private final OpBillingComponentRepository componentRepository;
    private final InvoiceNumberService invoiceNumberService;

    public OpDirectBillingService(
            OpDirectBillingRepository repository,
            PatientRepository patientRepository,
            ConsultantRepository consultantRepository,
            OpBillingComponentRepository componentRepository,
            InvoiceNumberService invoiceNumberService) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.consultantRepository = consultantRepository;
        this.componentRepository = componentRepository;
        this.invoiceNumberService = invoiceNumberService;
    }

    @Transactional
    public OpDirectBillingReceiptDto create(OpDirectBillingRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + request.patientId()));

        OpDirectBilling billing = new OpDirectBilling();
        billing.setPatient(patient);
        if (request.consultantId() != null) {
            Consultant consultant = consultantRepository.findById(request.consultantId())
                    .orElseThrow(() -> new EntityNotFoundException("Consultant not found: " + request.consultantId()));
            billing.setConsultant(consultant);
        }
        billing.setPaymentMode(request.paymentMode());
        billing.setRemarks(request.remarks());
        billing.setBilledAt(Instant.now());
        billing.setBilledBy(currentUsername());
        billing.setInvoiceNumber(invoiceNumberService.next());

        double total = 0;
        List<OpDirectBillingItem> items = new ArrayList<>();
        for (OpDirectBillingItemRequest itemRequest : request.items()) {
            OpBillingComponent component = componentRepository.findById(itemRequest.componentId())
                    .orElseThrow(() -> new EntityNotFoundException("OP Billing Component not found: " + itemRequest.componentId()));
            OpDirectBillingItem item = new OpDirectBillingItem();
            item.setBilling(billing);
            item.setComponent(component);
            item.setCategoryName(component.getCategory().getName());
            item.setComponentName(component.getName());
            item.setQuantity(itemRequest.quantity());
            item.setAmount(itemRequest.amount());
            item.setRemarks(itemRequest.remarks());
            if (itemRequest.consultantId() != null) {
                Consultant itemConsultant = consultantRepository.findById(itemRequest.consultantId())
                        .orElseThrow(() -> new EntityNotFoundException("Consultant not found: " + itemRequest.consultantId()));
                item.setConsultant(itemConsultant);
            }
            items.add(item);
            total += itemRequest.amount();
        }
        billing.setItems(items);
        billing.setTotalAmount(total);

        return toReceipt(repository.save(billing));
    }

    public OpDirectBillingReceiptDto findById(Long id) {
        return toReceipt(repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("OP Direct Billing not found: " + id)));
    }

    // Patient Information (OP/IP) screen's Billing Details tab.
    public List<OpDirectBillingListEntryDto> findByPatientId(Long patientId) {
        return repository.findByPatientIdOrderByBilledAtDesc(patientId).stream().map(this::toListEntry).toList();
    }

    /** Feeds the Appointments screen's "Direct Billing" tab - same date-range convention as AppointmentService.search(). */
    public List<OpDirectBillingListEntryDto> findAll(LocalDate fromDate, LocalDate toDate) {
        Instant fromInstant = fromDate != null ? fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Instant toInstant =
                toDate != null ? toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        return repository.collectionReport(fromInstant, toInstant, null, null).stream()
                .map(this::toListEntry)
                .toList();
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private OpDirectBillingListEntryDto toListEntry(OpDirectBilling billing) {
        Patient patient = billing.getPatient();
        Consultant consultant = billing.getConsultant();
        return new OpDirectBillingListEntryDto(
                billing.getId(),
                billing.getInvoiceNumber(),
                (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim(),
                patient.getRegistrationNumber(),
                billing.getTotalAmount(),
                billing.getPaymentMode(),
                billing.getBilledBy(),
                billing.getBilledAt(),
                billing.getRefundAmount(),
                consultant != null ? consultant.getId() : null,
                consultant != null ? consultant.getName() : null);
    }

    private OpDirectBillingReceiptDto toReceipt(OpDirectBilling billing) {
        Patient patient = billing.getPatient();
        Consultant consultant = billing.getConsultant();
        List<OpDirectBillingItemDto> items = billing.getItems().stream()
                .map(item -> new OpDirectBillingItemDto(
                        item.getId(),
                        item.getCategoryName(),
                        item.getComponentName(),
                        item.getQuantity(),
                        item.getAmount(),
                        item.getRemarks(),
                        item.getConsultant() != null ? item.getConsultant().getId() : null,
                        item.getConsultant() != null ? item.getConsultant().getName() : null))
                .toList();
        return new OpDirectBillingReceiptDto(
                billing.getId(),
                billing.getInvoiceNumber(),
                patient.getRegistrationNumber(),
                (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim(),
                patient.getGender(),
                patient.getAge(),
                patient.getMobileNumber(),
                consultant != null ? consultant.getId() : null,
                consultant != null ? consultant.getName() : null,
                items,
                billing.getTotalAmount(),
                billing.getPaymentMode(),
                billing.getRemarks(),
                billing.getBilledBy(),
                billing.getBilledAt(),
                billing.getRefundAmount());
    }
}
