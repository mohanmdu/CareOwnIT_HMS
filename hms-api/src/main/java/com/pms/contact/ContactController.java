package com.pms.contact;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated by construction - falls under ModulePathMappings' existing
 * "/api/public/" wildcard, no further wiring needed there (same as
 * com.pms.branding.PublicBrandingController). Called cross-origin from the
 * separate careownitsolutions.com marketing site (see SecurityConfig's CORS
 * doc comment - that origin is already allow-listed).
 */
@RestController
@RequestMapping("/api/public/contact")
public class ContactController {

    private final ContactEnquiryService service;

    public ContactController(ContactEnquiryService service) {
        this.service = service;
    }

    @PostMapping
    public ContactResponse submit(@Valid @RequestBody ContactRequest request, HttpServletRequest httpRequest) {
        service.submit(request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return new ContactResponse(true, "Thanks! We've received your request and will be in touch shortly.");
    }

    /** X-Forwarded-For first - production terminates TLS at a reverse proxy (same precedent as DomainTenantResolutionFilter.hostOf), which forwards the real client IP there rather than in getRemoteAddr(). */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
