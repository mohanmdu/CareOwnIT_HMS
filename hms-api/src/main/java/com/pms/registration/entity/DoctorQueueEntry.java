package com.pms.registration.entity;

import com.pms.common.Auditable;
import com.pms.masters.entity.Consultant;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "doctor_queue_entry")
@Getter
@Setter
@NoArgsConstructor
public class DoctorQueueEntry extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultant_id", nullable = false)
    private Consultant consultant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** Null for walk-ins. At most one queue entry per appointment (unique constraint). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    // Denormalized at check-in time, deliberately NOT recomputed from the
    // live Consultant.queueTokenPrefix later - editing a doctor's prefix in
    // Consultant Master must never retroactively rewrite historical tokens.
    @Column(name = "token_display", nullable = false, length = 20)
    private String tokenDisplay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DoctorQueueSource source;

    /** The Appointment's original slotTime, copied at check-in - preserved so a late check-in is still visible as such. Null for walk-ins. */
    @Column(name = "scheduled_slot_time")
    private LocalTime scheduledSlotTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DoctorQueueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DoctorQueuePriority priority = DoctorQueuePriority.NORMAL;

    @Column(name = "priority_reason", length = 255)
    private String priorityReason;

    /** Token-assignment / arrival moment - the primary FIFO ordering key. */
    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "recall_count", nullable = false)
    private int recallCount = 0;

    /** Set to the calledAt value at Complete-time - IN_CONSULTATION's start. */
    @Column(name = "consultation_started_at")
    private Instant consultationStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Shared free-text reason field for CANCEL / NO_SHOW (only one is ever populated at a time). */
    @Column(length = 255)
    private String reason;

    @Version
    private Long version;
}
