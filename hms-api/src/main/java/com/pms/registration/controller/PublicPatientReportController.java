package com.pms.registration.controller;

import com.pms.registration.service.PatientReportService;
import com.pms.security.InvalidCredentialsException;
import com.pms.security.JwtService;
import com.pms.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated by design (see ModulePathMappings.PUBLIC_PREFIXES'
 * "/api/public/" entry) - the one deliberate, narrow exception to patient
 * reports being private (see FileStorageService.PRIVATE_CATEGORIES). Only
 * reachable with a valid, time-limited token minted by
 * PatientReportService.createShareLink() - never by session/JWT, since
 * whoever opens this link (e.g. the patient, via WhatsApp) has no HMS
 * login at all. The token itself carries which tenant the report belongs
 * to, since there's no session here to read that from otherwise.
 */
@RestController
@RequestMapping("/api/public/patient-reports")
public class PublicPatientReportController {

    private final JwtService jwtService;
    private final PatientReportService service;

    public PublicPatientReportController(JwtService jwtService, PatientReportService service) {
        this.jwtService = jwtService;
        this.service = service;
    }

    @GetMapping("/{id}/{token}")
    public ResponseEntity<Resource> get(@PathVariable Long id, @PathVariable String token) {
        Claims claims = jwtService.parse(token);
        if (!"report-share".equals(claims.getSubject()) || !id.equals(claims.get("reportId", Long.class))) {
            throw new InvalidCredentialsException("This link is invalid or has expired.");
        }
        Long clientId = claims.get("clientId", Long.class);
        Resource resource = TenantContext.runAs(clientId, () -> service.resolveFileForSharing(id));
        return ResponseEntity.ok().body(resource);
    }
}
