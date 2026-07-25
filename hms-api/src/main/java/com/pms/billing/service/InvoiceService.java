package com.pms.billing.service;

import com.pms.billing.dto.InvoiceDto;
import com.pms.billing.dto.InvoiceLineItemDto;
import com.pms.billing.entity.BillingItem;
import com.pms.billing.entity.Invoice;
import com.pms.billing.entity.InvoiceLineItem;
import com.pms.billing.entity.InvoiceStatus;
import com.pms.billing.repository.BillingItemRepository;
import com.pms.billing.repository.InvoiceRepository;
import com.pms.common.EntityNotFoundException;
import com.pms.registration.entity.Appointment;
import com.pms.registration.entity.Patient;
import com.pms.registration.repository.AppointmentRepository;
import com.pms.registration.repository.PatientRepository;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the legacy OP billing flow spread across AdminAction (billing
 * category/component CRUD) and AdminDaoImpl.getInvoiceDetails /
 * updateInvoiceDetailsForRefund (migration doc §4.1). One invoice IS the
 * receipt - printing is a read of a PAID invoice, not a separate table/flow.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository repository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingItemRepository billingItemRepository;
    private final AtomicInteger sequence = new AtomicInteger(0);

    public InvoiceService(
            InvoiceRepository repository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            BillingItemRepository billingItemRepository) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.billingItemRepository = billingItemRepository;
        this.sequence.set((int) repository.count());
    }

    public List<InvoiceDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public InvoiceDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    // Patient Information (OP/IP) screen's Billing Details tab.
    public List<InvoiceDto> findByPatientId(Long patientId) {
        return repository.findByPatientIdOrderByCreatedAtDesc(patientId).stream().map(this::toDto).toList();
    }

    @Transactional
    public InvoiceDto create(InvoiceDto dto) {
        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + dto.patientId()));

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(nextInvoiceNumber());
        invoice.setPatient(patient);
        invoice.setStatus(InvoiceStatus.DRAFT);

        if (dto.appointmentId() != null) {
            Appointment appointment = appointmentRepository.findById(dto.appointmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + dto.appointmentId()));
            invoice.setAppointment(appointment);
        }

        double total = 0;
        List<InvoiceLineItem> lineItems = new ArrayList<>();
        for (InvoiceLineItemDto lineItemDto : dto.lineItems()) {
            BillingItem billingItem = billingItemRepository.findById(lineItemDto.billingItemId())
                    .orElseThrow(() -> new EntityNotFoundException("Billing item not found: " + lineItemDto.billingItemId()));
            InvoiceLineItem lineItem = new InvoiceLineItem();
            lineItem.setInvoice(invoice);
            lineItem.setBillingItem(billingItem);
            lineItem.setItemName(billingItem.getName());
            lineItem.setUnitPrice(billingItem.getPrice());
            lineItem.setQuantity(lineItemDto.quantity());
            double lineTotal = billingItem.getPrice() * lineItemDto.quantity();
            lineItem.setLineTotal(lineTotal);
            total += lineTotal;
            lineItems.add(lineItem);
        }
        invoice.setLineItems(lineItems);
        invoice.setTotalAmount(total);

        return toDto(repository.save(invoice));
    }

    @Transactional
    public InvoiceDto pay(Long id) {
        Invoice invoice = getOrThrow(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalArgumentException("Only DRAFT invoices can be paid, current status: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.PAID);
        return toDto(repository.save(invoice));
    }

    @Transactional
    public InvoiceDto cancel(Long id, String reason) {
        Invoice invoice = getOrThrow(id);
        invoice.setStatus(invoice.getStatus() == InvoiceStatus.PAID ? InvoiceStatus.REFUNDED : InvoiceStatus.CANCELLED);
        invoice.setCancellationReason(reason);
        return toDto(repository.save(invoice));
    }

    private String nextInvoiceNumber() {
        return "INV-" + Year.now().getValue() + "-" + String.format("%05d", sequence.incrementAndGet());
    }

    private Invoice getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + id));
    }

    private InvoiceDto toDto(Invoice invoice) {
        List<InvoiceLineItemDto> lineItems = invoice.getLineItems().stream()
                .map(li -> new InvoiceLineItemDto(
                        li.getId(), li.getBillingItem().getId(), li.getItemName(), li.getQuantity(), li.getUnitPrice(), li.getLineTotal()))
                .toList();
        return new InvoiceDto(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getPatient().getId(),
                (invoice.getPatient().getFirstName() + " " + (invoice.getPatient().getLastName() != null ? invoice.getPatient().getLastName() : "")).trim(),
                invoice.getAppointment() != null ? invoice.getAppointment().getId() : null,
                invoice.getStatus(),
                invoice.getTotalAmount(),
                invoice.getCancellationReason(),
                lineItems,
                invoice.getCreatedAt());
    }
}
