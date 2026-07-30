package com.pms.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dedicated safe-counter row per (consultant, date) - see DoctorQueueTokenService.
 * consultant_id is a plain FK column (not a mapped @ManyToOne) deliberately:
 * this table is locked on the hot check-in path and is never read/returned via
 * any API, so there's no reason to pay for entity-graph traversal here.
 */
@Entity
@Table(name = "doctor_queue_counter")
@Getter
@Setter
@NoArgsConstructor
public class DoctorQueueCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultant_id", nullable = false)
    private Long consultantId;

    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "last_token_number", nullable = false)
    private int lastTokenNumber = 0;
}
