package com.pms.website.controller;

import com.pms.website.dto.PatientLookupRequest;
import com.pms.website.dto.PatientLookupResult;
import com.pms.website.dto.PublicBookingRequest;
import com.pms.website.dto.PublicBookingResult;
import com.pms.website.dto.PublicSlotDto;
import com.pms.website.service.PublicAppointmentService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Unauthenticated by design (see SecurityConfig / ModulePathMappings'
 * PUBLIC_PREFIXES) - the public website's appointment booking flow.
 * Deliberately no login/OTP in front of this; see the Public Appointment
 * Booking plan for why.
 */
@RestController
@RequestMapping("/api/public/appointments")
public class PublicAppointmentController {

    private final PublicAppointmentService service;

    public PublicAppointmentController(PublicAppointmentService service) {
        this.service = service;
    }

    @GetMapping("/slots")
    public List<PublicSlotDto> slots(
            @RequestParam Long consultantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.slots(consultantId, date);
    }

    @PostMapping("/patient-lookup")
    public List<PatientLookupResult> lookupByMobile(@Valid @RequestBody PatientLookupRequest request) {
        return service.lookupByMobile(request.mobileNumber());
    }

    @PostMapping("/book")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicBookingResult book(@Valid @RequestBody PublicBookingRequest request) {
        return service.book(request);
    }
}
