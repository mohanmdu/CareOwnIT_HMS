package com.pms.registration.entity;

/**
 * Which module/screen a patient was first registered against - set once at
 * creation in PatientService.register() and never updated afterward. This is
 * provenance ("where did they start"), not a live "current location" - that's
 * already tracked correctly per-module (Admission.status, LabRequisition
 * status, etc).
 */
public enum PatientOriginModule {
    FRONT_OFFICE,
    OP_APPOINTMENT,
    IP_ADMISSION,
    PHARMACY,
    LAB,
    INSURANCE,
    WEBSITE
}
