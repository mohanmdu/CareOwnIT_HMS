package com.pms.billing.repository;

import com.pms.billing.entity.Invoice;
import com.pms.billing.entity.InvoiceStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Replaces the legacy AdminAction.getCollectionReport's Java-loop
    // aggregation (migration doc risk R12) with a real SQL SUM().
    @Query("select coalesce(sum(i.totalAmount), 0) from Invoice i where i.status = :status")
    double sumTotalAmountByStatus(InvoiceStatus status);

    // Patient Information (OP/IP) screen's Billing Details tab.
    List<Invoice> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}
