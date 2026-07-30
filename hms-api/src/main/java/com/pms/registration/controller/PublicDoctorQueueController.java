package com.pms.registration.controller;

import com.pms.registration.dto.DoctorQueueNowServingDto;
import com.pms.registration.service.DoctorQueueService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated by design (see SecurityConfig / ModulePathMappings'
 * PUBLIC_PREFIXES) - backs the Doctor Queue display board, a kiosk screen
 * meant to run unattended on a TV/monitor outside a consulting room with no
 * staff login in front of it. Returns only a token number and doctor name -
 * never patient identity - see DoctorQueueNowServingDto.
 */
@RestController
@RequestMapping("/api/public/doctor-queue")
public class PublicDoctorQueueController {

    private final DoctorQueueService service;

    public PublicDoctorQueueController(DoctorQueueService service) {
        this.service = service;
    }

    @GetMapping("/now-serving")
    public DoctorQueueNowServingDto nowServing(
            @RequestParam Long consultantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.boardView(consultantId, date != null ? date : LocalDate.now());
    }
}
