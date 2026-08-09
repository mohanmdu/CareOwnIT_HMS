package com.pms.registration.entity;

import com.pms.common.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A doctor's OP case sheet for one appointment OR one OP Direct Billing
 * walk-in visit (migration doc's "Patient Prescription" screens): vitals,
 * assessment, diagnosis, and a repeating drug-prescription grid. Exactly one
 * of appointment/opDirectBilling is set (enforced by chk_op_case_sheet_source
 * - same dual-parent shape as Refund), each individually unique
 * (uq_op_case_sheet_appointment / uq_op_case_sheet_op_direct_billing) - one
 * case sheet per visit, since an appointment/walk-in already pairs one
 * patient with (at most) one consultant for that visit.
 */
@Entity
@Table(name = "op_case_sheet")
@Getter
@Setter
@NoArgsConstructor
public class OpCaseSheet extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "op_direct_billing_id")
    private OpDirectBilling opDirectBilling;

    @Column(name = "food_drug_allergy", length = 500)
    private String foodDrugAllergy;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    private Double bmi;

    @Column(name = "temperature_f")
    private Double temperatureF;

    @Column(name = "pulse_bpm")
    private Integer pulseBpm;

    @Column(name = "respiration_bpm")
    private Integer respirationBpm;

    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    private Double spo2;

    @Column(name = "body_fat_percent")
    private Double bodyFatPercent;

    @Column(name = "chief_complaints", length = 1000)
    private String chiefComplaints;

    @Column(name = "past_medical_condition", length = 1000)
    private String pastMedicalCondition;

    @Column(name = "current_medication", length = 1000)
    private String currentMedication;

    @Column(name = "physical_activity")
    private String physicalActivity;

    @Column(name = "sleep_duration_hours", length = 50)
    private String sleepDurationHours;

    @Column(length = 10)
    private String smoking;

    @Column(length = 10)
    private String alcohol;

    private String surgery;

    @Column(name = "family_history")
    private String familyHistory;

    @Column(name = "provisional_diagnosis", length = 500)
    private String provisionalDiagnosis;

    @Column(length = 100)
    private String cbg;

    @Column(length = 1000)
    private String findings;

    @Column(length = 1000)
    private String investigation;

    @Column(name = "doctor_notes_1", length = 1000)
    private String doctorNotes1;

    @Column(name = "doctor_notes_2", length = 1000)
    private String doctorNotes2;

    @Column(length = 1000)
    private String diet;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @OneToMany(mappedBy = "caseSheet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<OpPrescriptionItem> prescriptionItems = new ArrayList<>();
}
