package com.pms.registration.entity;

import com.pms.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Consolidates the legacy com.pms.model.PatientDetails / PatientDetailsOP /
 * PatientCommonDetails split (separate near-duplicate models per
 * registration flavor) into one entity - see migration doc §4.1, §5. The
 * legacy NF-ID lookup pattern becomes a simple unique registration number.
 */
@Entity
@Table(name = "patient")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    private LocalDate dateOfBirth;

    private String gender;

    private Integer age;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    private String email;

    private String address;

    @Column(nullable = false)
    private boolean active = true;

    /** Set once at creation in PatientService.register(); update() never touches this. */
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_module", nullable = false, length = 32)
    private PatientOriginModule originModule = PatientOriginModule.FRONT_OFFICE;

    /** "/uploads/..." relative path from FileStorageService, same convention as Admission.photoPath - set via uploadPhoto(), null until then. */
    @Column(name = "photo_path")
    private String photoPath;
}
