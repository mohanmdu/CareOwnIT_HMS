package com.pms.branding;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated by construction - falls under ModulePathMappings' existing
 * "/api/public/" wildcard, no further wiring needed there. See
 * PublicBrandingService for why an unknown clientCode returns 200 with an
 * empty body instead of 404.
 */
@RestController
@RequestMapping("/api/public/branding")
public class PublicBrandingController {

    private final PublicBrandingService service;

    public PublicBrandingController(PublicBrandingService service) {
        this.service = service;
    }

    @GetMapping
    public PublicBrandingDto get(@RequestParam String clientCode) {
        return service.getByClientCode(clientCode);
    }
}
