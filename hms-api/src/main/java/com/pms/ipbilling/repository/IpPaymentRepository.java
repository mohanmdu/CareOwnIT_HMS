package com.pms.ipbilling.repository;

import com.pms.ipbilling.entity.IpPayment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IpPaymentRepository extends JpaRepository<IpPayment, Long> {
    List<IpPayment> findByAdmissionIdOrderByPaymentDateDesc(Long admissionId);

    long countByAdmissionId(Long admissionId);

    // Cashier Collection Report - every IP payment transaction in a date range, across all admissions.
    List<IpPayment> findByPaymentDateBetweenOrderByPaymentDateDesc(Instant from, Instant to);
}
