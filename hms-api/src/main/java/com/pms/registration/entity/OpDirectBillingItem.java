package com.pms.registration.entity;

import com.pms.masters.entity.Consultant;
import com.pms.masters.entity.OpBillingComponent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One priced line in an OpDirectBilling invoice. categoryName/componentName
 * are snapshotted at billing time (same reasoning as PatientAuditLog.patientName)
 * so a later master rename/deactivation doesn't rewrite billing history.
 */
@Entity
@Table(name = "op_direct_billing_item")
@Getter
@Setter
@NoArgsConstructor
public class OpDirectBillingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "op_direct_billing_id", nullable = false)
    private OpDirectBilling billing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private OpBillingComponent component;

    /** Optional - separate from OpDirectBilling.consultant (one per whole bill); this is per line item. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultant_id")
    private Consultant consultant;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(nullable = false)
    private Double amount;

    @Column(length = 255)
    private String remarks;
}
