package com.pms.ipbilling.entity;

import com.pms.common.Auditable;
import com.pms.ipadmission.entity.Admission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One transaction in the PAYMENTS history ledger (PDF p.11-12) - a receipt
 * row. Created whenever a payment is collected against an admission
 * (AdmissionService.recordCashierPayment), so this ledger reflects real
 * transaction history rather than only the single running advanceAmount total.
 */
@Entity
@Table(name = "ip_payment")
@Getter
@Setter
@NoArgsConstructor
public class IpPayment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    private Admission admission;

    @Column(name = "payment_date", nullable = false)
    private Instant paymentDate;

    @Column(name = "receipt_number", nullable = false, unique = true)
    private String receiptNumber;

    @Column
    private String description;

    @Column(name = "payment_type")
    private String paymentType;

    @Column(name = "invoiced_amount", nullable = false)
    private double invoicedAmount = 0;

    @Column(name = "refund_amount", nullable = false)
    private double refundAmount = 0;

    @Column(name = "net_amount", nullable = false)
    private double netAmount = 0;

    @Column(name = "created_by")
    private String createdBy;
}
