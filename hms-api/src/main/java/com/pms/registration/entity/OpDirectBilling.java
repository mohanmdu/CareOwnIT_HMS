package com.pms.registration.entity;

import com.pms.masters.entity.Consultant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Walk-in OP billing not tied to a doctor appointment (migration doc's OP
 * Direct Billing module) - registration fees, lab charges, misc OP charges.
 * Billed immediately on creation (no separate pending/approval step, unlike
 * Appointment - there's no doctor/slot to approve here), sharing the same
 * invoice-number sequence as Appointment billing via InvoiceNumberService.
 */
@Entity
@Table(name = "op_direct_billing")
@Getter
@Setter
@NoArgsConstructor
public class OpDirectBilling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** Optional - most walk-in charges have no doctor involved; staff can attribute one when relevant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultant_id")
    private Consultant consultant;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private Long invoiceNumber;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 32)
    private PaymentMode paymentMode;

    @Column(length = 500)
    private String remarks;

    @Column(name = "billed_by", length = 100)
    private String billedBy;

    @Column(name = "billed_at", nullable = false)
    private Instant billedAt;

    // Reserved for the Refund flow, mirrors Appointment.refundAmount's role.
    @Column(name = "refund_amount")
    private Double refundAmount;

    @OneToMany(mappedBy = "billing", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OpDirectBillingItem> items = new ArrayList<>();
}
