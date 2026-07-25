package com.pms.website.service;

import com.pms.common.EntityNotFoundException;
import com.pms.registration.dto.AppointmentDto;
import com.pms.registration.dto.AppointmentSlotDto;
import com.pms.registration.dto.PatientDto;
import com.pms.registration.entity.Patient;
import com.pms.registration.entity.PatientOriginModule;
import com.pms.registration.repository.PatientRepository;
import com.pms.registration.service.AppointmentAvailabilityService;
import com.pms.registration.service.AppointmentService;
import com.pms.registration.service.PatientService;
import com.pms.website.dto.PatientLookupResult;
import com.pms.website.dto.PublicBookingRequest;
import com.pms.website.dto.PublicBookingResult;
import com.pms.website.dto.PublicSlotDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anonymous public booking - reuses PatientService.register()/
 * AppointmentService.book() as-is rather than duplicating their logic (slot-
 * race guard, registration-number generation, audit logging all stay in one
 * place). This service's own job is just: trim what an anonymous caller may
 * see, and resolve-or-create the patient identity from a bare mobile number
 * since there's no login here at all (see the Public Appointment Booking
 * plan - deliberately no OTP/auth in front of this).
 */
@Service
@Transactional(readOnly = true)
public class PublicAppointmentService {

    private final PatientRepository patientRepository;
    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final AppointmentAvailabilityService availabilityService;

    public PublicAppointmentService(
            PatientRepository patientRepository,
            PatientService patientService,
            AppointmentService appointmentService,
            AppointmentAvailabilityService availabilityService) {
        this.patientRepository = patientRepository;
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.availabilityService = availabilityService;
    }

    public List<PublicSlotDto> slots(Long consultantId, LocalDate date) {
        return availabilityService.slots(consultantId, date).stream().map(this::toPublicSlot).toList();
    }

    /** Mobile number is not unique (e.g. a parent booking for multiple children) - callers must let the visitor pick from however many matches come back, including zero. */
    public List<PatientLookupResult> lookupByMobile(String mobileNumber) {
        return patientRepository.findByMobileNumber(mobileNumber).stream()
                .filter(Patient::isActive)
                .map(this::toLookupResult)
                .toList();
    }

    @Transactional
    public PublicBookingResult book(PublicBookingRequest request) {
        Long patientId = resolvePatientId(request);
        // Only patientId/consultantId/appointmentDate/slotTime are read by
        // AppointmentService.book() - everything else on this DTO is
        // response-side (names, billing fields, etc.) and gets filled in by
        // its own toDto() mapping from the persisted entities, so every
        // other positional argument here is deliberately null.
        AppointmentDto booked = appointmentService.book(
                new AppointmentDto(
                        null, patientId, null, null, null, null,
                        request.consultantId(), null, null,
                        request.date(), request.slotTime(),
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        return new PublicBookingResult(
                booked.id(), booked.patientName(), booked.consultantName(), booked.appointmentDate(), booked.slotTime());
    }

    /**
     * The only "credential" an anonymous booking has is knowledge of the
     * mobile number, so an existingPatientId claim is re-verified against
     * that patient's real mobile number server-side, never trusted as-is
     * from the client - otherwise anyone could pass an arbitrary patientId.
     */
    private Long resolvePatientId(PublicBookingRequest request) {
        if (request.existingPatientId() != null) {
            Patient patient = patientRepository.findById(request.existingPatientId())
                    .filter(Patient::isActive)
                    .orElseThrow(() -> new EntityNotFoundException("Patient not found: " + request.existingPatientId()));
            if (!request.mobileNumber().equals(patient.getMobileNumber())) {
                throw new IllegalArgumentException("Mobile number does not match the selected patient.");
            }
            return patient.getId();
        }

        if (request.newPatient() == null) {
            throw new IllegalArgumentException("Either an existing patient or new patient details are required.");
        }
        var details = request.newPatient();
        PatientDto created = patientService.register(new PatientDto(
                null, null,
                details.firstName(), details.lastName(), details.dateOfBirth(), details.gender(), details.age(),
                request.mobileNumber(), details.email(), details.address(), true,
                PatientOriginModule.WEBSITE));
        return created.id();
    }

    private PublicSlotDto toPublicSlot(AppointmentSlotDto slot) {
        return new PublicSlotDto(slot.date(), slot.time(), slot.session(), slot.status());
    }

    private PatientLookupResult toLookupResult(Patient patient) {
        String name = (patient.getFirstName() + " " + (patient.getLastName() != null ? patient.getLastName() : "")).trim();
        return new PatientLookupResult(patient.getId(), name, patient.getAge(), patient.getGender());
    }
}
