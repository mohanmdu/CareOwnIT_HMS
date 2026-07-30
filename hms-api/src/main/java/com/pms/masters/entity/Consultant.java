package com.pms.masters.entity;

import com.pms.common.Auditable;
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
 * Replaces the legacy com.pms.model.Consultant (table consultant). The legacy
 * entity stored the department as a free-text name/code (CONSLT_DEPT,
 * CONSLT_DEPT_NAME); this modernized version uses a real foreign key to
 * Department instead. Weekly working hours live separately in
 * ConsultantTiming - actual booking-availability enforcement against those
 * hours is still deferred to the Appointments module (Phase 2 order 2, see
 * migration doc §4.6/§5); this entity only carries the admin-editable hours.
 */
@Entity
@Table(name = "consultant")
@Getter
@Setter
@NoArgsConstructor
public class Consultant extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialization_id")
    private Specialization specialization;

    @Column
    private String email;

    @Column
    private String mobileNumber;

    @Column(name = "consultation_fee")
    private double consultationFee;

    @Column(columnDefinition = "TEXT")
    private String profile;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "accepting_appointments", nullable = false)
    private boolean acceptingAppointments = true;

    @Column(name = "slots_per_hour", nullable = false)
    private int slotsPerHour = 1;

    @Column(name = "image_path")
    private String imagePath;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "published_to_web", nullable = false)
    private boolean publishedToWeb = false;

    // Configurable Doctor Queue token prefix (e.g. "K" in "K001") - defaults
    // to the doctor's name-initial if left blank, see ConsultantService.
    @Column(name = "queue_token_prefix", nullable = false, length = 10)
    private String queueTokenPrefix;

    // Optional consulting room/cabin label (e.g. "Room 3") shown on this
    // doctor's Doctor Queue display board - unlike queueTokenPrefix, blank
    // is a valid "no room set" state, so there's no default-resolution logic.
    @Column(name = "consulting_room_label", length = 50)
    private String consultingRoomLabel;
}
